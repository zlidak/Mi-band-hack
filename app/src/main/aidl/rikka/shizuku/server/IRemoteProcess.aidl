package rikka.shizuku.server;

interface IRemoteProcess {
    int waitFor();
    boolean destroy();
    ParcelFileDescriptor getInputStream();
    ParcelFileDescriptor getOutputStream();
    ParcelFileDescriptor getErrorStream();
}