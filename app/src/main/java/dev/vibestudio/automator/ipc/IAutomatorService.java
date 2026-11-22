package dev.vibestudio.automator.ipc;
import android.os.*;
/** Hand-written equivalent of IAutomatorService.aidl (transaction order matches the .aidl). */
public interface IAutomatorService extends IInterface {
    String DESCRIPTOR = "dev.vibestudio.automator.ipc.IAutomatorService";
    int F = IBinder.FIRST_CALL_TRANSACTION;
    int TX_STATUS = F, TX_EXEC = F + 1, TX_STOP = F + 2, TX_PAUSE = F + 3, TX_RESUME = F + 4,
        TX_STATE = F + 5, TX_REG = F + 6, TX_UNREG = F + 7, TX_LOGS = F + 8;
    String getDriverStatus() throws RemoteException;
    void executeTool(String toolName, String argsJson, IAutomatorCallback callback) throws RemoteException;
    void emergencyStop() throws RemoteException;
    void pause() throws RemoteException;
    void resume() throws RemoteException;
    String getState() throws RemoteException;
    void registerLogListener(IAutomatorLogListener l) throws RemoteException;
    void unregisterLogListener(IAutomatorLogListener l) throws RemoteException;
    String getLogs() throws RemoteException;

    abstract class Stub extends Binder implements IAutomatorService {
        public Stub() { attachInterface(this, DESCRIPTOR); }
        public IBinder asBinder() { return this; }
        public static IAutomatorService asInterface(IBinder b) {
            if (b == null) return null;
            IInterface i = b.queryLocalInterface(DESCRIPTOR);
            if (i instanceof IAutomatorService) return (IAutomatorService) i;
            return new Proxy(b);
        }
        @Override protected boolean onTransact(int code, Parcel d, Parcel r, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { r.writeString(DESCRIPTOR); return true; }
            if (code < F || code > TX_LOGS) return super.onTransact(code, d, r, flags);
            d.enforceInterface(DESCRIPTOR);
            switch (code) {
                case TX_STATUS: { String s = getDriverStatus(); r.writeNoException(); r.writeString(s); return true; }
                case TX_EXEC: { String a = d.readString(), b = d.readString();
                    IAutomatorCallback cb = IAutomatorCallback.Stub.asInterface(d.readStrongBinder());
                    executeTool(a, b, cb); r.writeNoException(); return true; }
                case TX_STOP: emergencyStop(); r.writeNoException(); return true;
                case TX_PAUSE: pause(); r.writeNoException(); return true;
                case TX_RESUME: resume(); r.writeNoException(); return true;
                case TX_STATE: { String s = getState(); r.writeNoException(); r.writeString(s); return true; }
                case TX_REG: registerLogListener(IAutomatorLogListener.Stub.asInterface(d.readStrongBinder())); r.writeNoException(); return true;
                case TX_UNREG: unregisterLogListener(IAutomatorLogListener.Stub.asInterface(d.readStrongBinder())); r.writeNoException(); return true;
                default: { String s = getLogs(); r.writeNoException(); r.writeString(s); return true; }
            }
        }
        private static class Proxy implements IAutomatorService {
            private final IBinder b; Proxy(IBinder b) { this.b = b; }
            public IBinder asBinder() { return b; }
            private String callStr(int tx) throws RemoteException {
                Parcel d = Parcel.obtain(), r = Parcel.obtain();
                try { d.writeInterfaceToken(DESCRIPTOR); b.transact(tx, d, r, 0); r.readException(); return r.readString(); }
                finally { r.recycle(); d.recycle(); }
            }
            private void callVoid(int tx, IBinder arg) throws RemoteException {
                Parcel d = Parcel.obtain(), r = Parcel.obtain();
                try { d.writeInterfaceToken(DESCRIPTOR); if (arg != null || tx == TX_REG || tx == TX_UNREG) d.writeStrongBinder(arg);
                    b.transact(tx, d, r, 0); r.readException(); }
                finally { r.recycle(); d.recycle(); }
            }
            public String getDriverStatus() throws RemoteException { return callStr(TX_STATUS); }
            public void executeTool(String t, String a, IAutomatorCallback cb) throws RemoteException {
                Parcel d = Parcel.obtain(), r = Parcel.obtain();
                try { d.writeInterfaceToken(DESCRIPTOR); d.writeString(t); d.writeString(a);
                    d.writeStrongBinder(cb != null ? cb.asBinder() : null); b.transact(TX_EXEC, d, r, 0); r.readException(); }
                finally { r.recycle(); d.recycle(); }
            }
            public void emergencyStop() throws RemoteException { callVoid(TX_STOP, null); }
            public void pause() throws RemoteException { callVoid(TX_PAUSE, null); }
            public void resume() throws RemoteException { callVoid(TX_RESUME, null); }
            public String getState() throws RemoteException { return callStr(TX_STATE); }
            public void registerLogListener(IAutomatorLogListener l) throws RemoteException { callVoid(TX_REG, l == null ? null : l.asBinder()); }
            public void unregisterLogListener(IAutomatorLogListener l) throws RemoteException { callVoid(TX_UNREG, l == null ? null : l.asBinder()); }
            public String getLogs() throws RemoteException { return callStr(TX_LOGS); }
        }
    }
}
