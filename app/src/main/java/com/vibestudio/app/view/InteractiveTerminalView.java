package com.vibestudio.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Nullable;

import com.vibestudio.app.util.FontUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;

public class InteractiveTerminalView extends View {

    private static final String TAG = "InteractiveTerminal";

    public static class TermLine {
        public final String text;
        public final int color;

        public TermLine(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    private int mTerminalBackgroundColor = Color.parseColor("#1E1E2E");
    private int mTextColor = Color.parseColor("#CDD6F4");
    private int mErrorColor = Color.parseColor("#F38BA8");
    private int mPromptColor = Color.parseColor("#A6E3A1");
    private int mCursorColor = Color.parseColor("#F5C2E7");
    private float mTextSizeSp = 13f;
    private Typeface mFontFamily;
    private float mLineSpacing = 1.3f;

    private final List<TermLine> mLines = new ArrayList<>();
    private final int mMaxLines = 2000;
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCursorPaint = new Paint();

    private float mCharWidth = 0f;
    private float mCharHeight = 0f;

    private final StringBuilder mInputBuffer = new StringBuilder();
    private String mPartialLine = "";

    private OutputStream mProcessOutputStream;
    private Process mCurrentProcess;

    public InteractiveTerminalView(Context context) {
        super(context);
        init();
    }

    public InteractiveTerminalView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public InteractiveTerminalView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundColor(mTerminalBackgroundColor);
        mFontFamily = FontUtils.getMonospaceTypeface(getContext());
        mTextPaint.setColor(mTextColor);
        mTextPaint.setTypeface(mFontFamily);
        mCursorPaint.setColor(mCursorColor);
        updateTextSize();
    }

    public void attachProcess(Process process) {
        this.mCurrentProcess = process;
        this.mProcessOutputStream = process.getOutputStream();

        startInputStreamReader(process.getInputStream(), false);
        startInputStreamReader(process.getErrorStream(), true);
    }

    private void startInputStreamReader(InputStream inputStream, boolean isError) {
        new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    String str = new String(buffer, 0, len);
                    post(() -> appendTextChunk(str, isError));
                }
            } catch (InterruptedIOException e) {
                Log.d(TAG, "Process stream read interrupted on view detach/close");
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("closed")) {
                    Log.d(TAG, "Process stream closed: " + e.getMessage());
                } else {
                    Log.e(TAG, "Error reading process stream", e);
                }
            }
        }).start();
    }

    public synchronized void appendTextChunk(String chunk, boolean isError) {
        String combined = mPartialLine + chunk;
        String[] parts = combined.split("\r?\n", -1);

        for (int i = 0; i < parts.length - 1; i++) {
            mLines.add(new TermLine(parts[i], isError ? mErrorColor : mTextColor));
        }

        mPartialLine = parts[parts.length - 1];

        while (mLines.size() > mMaxLines) {
            mLines.remove(0);
        }
        invalidate();
    }

    public synchronized void appendLine(String text, boolean isError) {
        mLines.add(new TermLine(text, isError ? mErrorColor : mTextColor));
        while (mLines.size() > mMaxLines) {
            mLines.remove(0);
        }
        invalidate();
    }

    public synchronized void clear() {
        mLines.clear();
        mInputBuffer.setLength(0);
        mPartialLine = "";
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateTextSize();
    }

    private void updateTextSize() {
        mTextPaint.setTextSize(mTextSizeSp * getResources().getDisplayMetrics().scaledDensity);
        mCharWidth = mTextPaint.measureText("M");
        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        mCharHeight = fm.descent - fm.ascent;
    }

    private int calculateScrollStart() {
        int visibleLines = (int) (getHeight() / (mCharHeight * mLineSpacing));
        int totalDisplayLines = mLines.size() + 1; // including active bottom line
        return Math.max(0, totalDisplayLines - visibleLines + 1);
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(mTerminalBackgroundColor);

        int startLine = calculateScrollStart();
        int visibleCount = (int) (getHeight() / (mCharHeight * mLineSpacing)) + 2;

        int lineIdx = 0;
        float currentY = mCharHeight * mLineSpacing;

        for (TermLine line : mLines) {
            if (lineIdx >= startLine && lineIdx < startLine + visibleCount) {
                mTextPaint.setColor(line.color);
                canvas.drawText(line.text, getPaddingLeft(), currentY, mTextPaint);
            }
            lineIdx++;
            currentY += mCharHeight * mLineSpacing;
        }

        // Draw active bottom line (partial process output prompt + typed buffer)
        if (lineIdx >= startLine && lineIdx < startLine + visibleCount) {
            mTextPaint.setColor(mTextColor);
            String fullBottomText = mPartialLine + mInputBuffer.toString();
            canvas.drawText(fullBottomText, getPaddingLeft(), currentY, mTextPaint);

            // Draw cursor at the end of fullBottomText
            float cursorX = getPaddingLeft() + fullBottomText.length() * mCharWidth;
            canvas.drawRect(cursorX, currentY - mCharHeight, cursorX + mCharWidth, currentY, mCursorPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            requestFocus();
            showSoftKeyboard();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void sendInput(String data) {
        if (mProcessOutputStream != null) {
            try {
                mProcessOutputStream.write(data.getBytes());
                mProcessOutputStream.flush();
            } catch (Exception e) {
                Log.e(TAG, "Error writing to process stdin", e);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            String textToSend = mInputBuffer.toString() + "\n";
            // Append typed line into history line list immediately
            mLines.add(new TermLine(mPartialLine + mInputBuffer.toString(), mTextColor));
            mPartialLine = "";
            mInputBuffer.setLength(0);

            sendInput(textToSend);
            invalidate();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (mInputBuffer.length() > 0) {
                mInputBuffer.deleteCharAt(mInputBuffer.length() - 1);
                invalidate();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        outAttrs.imeOptions = EditorInfo.IME_ACTION_SEND;

        return new BaseInputConnection(this, true) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (text != null) {
                    if (text.toString().equals("\n")) {
                        onKeyDown(KeyEvent.KEYCODE_ENTER, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                    } else {
                        mInputBuffer.append(text);
                        invalidate();
                    }
                }
                return true;
            }

            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                for (int i = 0; i < beforeLength; i++) {
                    if (mInputBuffer.length() > 0) {
                        mInputBuffer.deleteCharAt(mInputBuffer.length() - 1);
                    }
                }
                invalidate();
                return true;
            }
        };
    }

    private void showSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        }
    }
}
