package dev.vibestudio.automator.ipc;
import android.os.*;
/** Hand-written equivalent of IAutomatorLogListener.aidl (oneway). */
public interface IAutomatorLogListener extends IInterface {
    String DESCRIPTOR = "dev.vibestudio.automator.ipc.IAutomatorLogListener";
    int TX_LOG = IBinder.FIRST_CALL_TRANSACTION;
    void onLog(String entryJson) throws RemoteException;

    abstract class Stub extends Binder implements IAutomatorLogListener {
        public Stub() { attachInterface(this, DESCRIPTOR); }
        public IBinder asBinder() { return this; }
        public static IAutomatorLogListener asInterface(IBinder b) {
            if (b == null) return null;
            IInterface i = b.queryLocalInterface(DESCRIPTOR);
            if (i instanceof IAutomatorLogListener) return (IAutomatorLogListener) i;
            return new Proxy(b);
        }
        @Override protected boolean onTransact(int code, Parcel d, Parcel r, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { r.writeString(DESCRIPTOR); return true; }
            d.enforceInterface(DESCRIPTOR);
            if (code == TX_LOG) { onLog(d.readString()); return true; }
            return super.onTransact(code, d, r, flags);
        }
        private static class Proxy implements IAutomatorLogListener {
            private final IBinder b; Proxy(IBinder b) { this.b = b; }
            public IBinder asBinder() { return b; }
            public void onLog(String s) throws RemoteException {
                Parcel d = Parcel.obtain(); try { d.writeInterfaceToken(DESCRIPTOR); d.writeString(s); b.transact(TX_LOG, d, null, IBinder.FLAG_ONEWAY); } finally { d.recycle(); }
            }
        }
    }
}
