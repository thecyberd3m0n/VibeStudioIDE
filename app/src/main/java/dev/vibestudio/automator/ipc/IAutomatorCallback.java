package dev.vibestudio.automator.ipc;
import android.os.*;
/** Hand-written equivalent of IAutomatorCallback.aidl (oneway). */
public interface IAutomatorCallback extends IInterface {
    String DESCRIPTOR = "dev.vibestudio.automator.ipc.IAutomatorCallback";
    int TX_SUCCESS = IBinder.FIRST_CALL_TRANSACTION, TX_ERROR = IBinder.FIRST_CALL_TRANSACTION + 1;
    void onSuccess(String resultJson) throws RemoteException;
    void onError(int errorCode, String message) throws RemoteException;

    abstract class Stub extends Binder implements IAutomatorCallback {
        public Stub() { attachInterface(this, DESCRIPTOR); }
        public IBinder asBinder() { return this; }
        public static IAutomatorCallback asInterface(IBinder b) {
            if (b == null) return null;
            IInterface i = b.queryLocalInterface(DESCRIPTOR);
            if (i instanceof IAutomatorCallback) return (IAutomatorCallback) i;
            return new Proxy(b);
        }
        @Override protected boolean onTransact(int code, Parcel d, Parcel r, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { r.writeString(DESCRIPTOR); return true; }
            d.enforceInterface(DESCRIPTOR);
            if (code == TX_SUCCESS) { onSuccess(d.readString()); return true; }
            if (code == TX_ERROR) { onError(d.readInt(), d.readString()); return true; }
            return super.onTransact(code, d, r, flags);
        }
        private static class Proxy implements IAutomatorCallback {
            private final IBinder b; Proxy(IBinder b) { this.b = b; }
            public IBinder asBinder() { return b; }
            public void onSuccess(String s) throws RemoteException {
                Parcel d = Parcel.obtain(); try { d.writeInterfaceToken(DESCRIPTOR); d.writeString(s); b.transact(TX_SUCCESS, d, null, IBinder.FLAG_ONEWAY); } finally { d.recycle(); }
            }
            public void onError(int c, String m) throws RemoteException {
                Parcel d = Parcel.obtain(); try { d.writeInterfaceToken(DESCRIPTOR); d.writeInt(c); d.writeString(m); b.transact(TX_ERROR, d, null, IBinder.FLAG_ONEWAY); } finally { d.recycle(); }
            }
        }
    }
}
