package rikka.shizuku.server;

import rikka.shizuku.server.IRemoteProcess;

interface IShizukuService {
    IRemoteProcess newProcess(in String[] cmd, in String[] env, String dir);
}