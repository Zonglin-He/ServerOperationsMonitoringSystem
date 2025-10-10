package org.example.utils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.BaseDetail;
import org.example.entity.ConnectionConfig;
import org.example.entity.RuntimeDetail;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Slf4j
@Component
public class MonitorUtils {

    @Lazy
    @Resource
    ConnectionConfig config;

    private final SystemInfo info = new SystemInfo();
    private final Properties properties = System.getProperties();
    public BaseDetail monitorBaseDetail() {
        OperatingSystem os = info.getOperatingSystem();
        HardwareAbstractionLayer hardware = info.getHardware();
        double memory = hardware.getMemory().getTotal() / 1024.0 / 1024 /1024;
        double diskSize = Arrays.stream(File.listRoots()).mapToLong(File::getTotalSpace).sum() / 1024.0 / 1024 / 1024;
        String ip = resolvePrimaryIp(hardware);

        return new BaseDetail()
                .setOsArch(properties.getProperty("os.arch"))
                .setOsName(os.getFamily())
                .setOsVersion(os.getVersionInfo().getVersion())
                .setOsBit(os.getBitness())
                .setCpuName(hardware.getProcessor().getProcessorIdentifier().getName())
                .setCpuCore(hardware.getProcessor().getLogicalProcessorCount())
                .setMemory(memory)
                .setDisk(diskSize)
                .setIp(ip);
    }

    public RuntimeDetail monitorRuntimeDetail() {
        double statisticTime = 0.5;
        try {
            HardwareAbstractionLayer hardware = info.getHardware();
            NetworkIF networkInterface = Objects.requireNonNull(this.findNetworkInterface(hardware));
            CentralProcessor processor = hardware.getProcessor();
            double upload = networkInterface.getBytesSent(), download = networkInterface.getBytesRecv();
            double read = hardware.getDiskStores().stream().mapToLong(HWDiskStore::getReadBytes).sum();
            double write = hardware.getDiskStores().stream().mapToLong(HWDiskStore::getWriteBytes).sum();
            long[] ticks = processor.getSystemCpuLoadTicks();
            Thread.sleep((long) (statisticTime * 1000));
            networkInterface = Objects.requireNonNull(this.findNetworkInterface(hardware));
            upload = (networkInterface.getBytesSent() - upload) / statisticTime;
            download =  (networkInterface.getBytesRecv() - download) / statisticTime;
            read = (hardware.getDiskStores().stream().mapToLong(HWDiskStore::getReadBytes).sum() - read) / statisticTime;
            write = (hardware.getDiskStores().stream().mapToLong(HWDiskStore::getWriteBytes).sum() - write) / statisticTime;
            double memory = (hardware.getMemory().getTotal() - hardware.getMemory().getAvailable()) / 1024.0 / 1024 / 1024;
            double disk = Arrays.stream(File.listRoots())
                    .mapToLong(file -> file.getTotalSpace() - file.getFreeSpace()).sum() / 1024.0 / 1024 / 1024;
            return new RuntimeDetail()
                    .setCpuUsage(this.calculateCpuUsage(processor, ticks))
                    .setMemoryUsage(memory)
                    .setDiskUsage(disk)
                    .setNetworkUpload(upload / 1024)
                    .setNetworkDownload(download / 1024)
                    .setDiskRead(read / 1024/ 1024)
                    .setDiskWrite(write / 1024 / 1024)
                    .setTimestamp(new Date().getTime());
        } catch (Exception e) {
            log.error("Fail to read runtime data", e);
        }
        return null;
    }

    private NetworkIF findNetworkInterface(HardwareAbstractionLayer hardware) {
        try {
            String target = config.getNetworkInterface();
            List<NetworkIF> ifs = hardware.getNetworkIFs()
                    .stream()
                    .filter(inter -> inter.getName().equals(target))
                    .toList();
            if (!ifs.isEmpty()) {
                return ifs.get(0);
            } else {
                throw new IOException("Network card information error, network card not found: " + target);
            }
        } catch (IOException e) {
            log.error("Error reading network interface information", e);
        }
        return null;
    }

    private double calculateCpuUsage(CentralProcessor processor, long[] prevTicks) {
        long[] ticks = processor.getSystemCpuLoadTicks();
        long user    = Math.max(0, ticks[CentralProcessor.TickType.USER.getIndex()]    - prevTicks[CentralProcessor.TickType.USER.getIndex()]);
        long nice    = Math.max(0, ticks[CentralProcessor.TickType.NICE.getIndex()]    - prevTicks[CentralProcessor.TickType.NICE.getIndex()]);
        long sys     = Math.max(0, ticks[CentralProcessor.TickType.SYSTEM.getIndex()]  - prevTicks[CentralProcessor.TickType.SYSTEM.getIndex()]);
        long irq     = Math.max(0, ticks[CentralProcessor.TickType.IRQ.getIndex()]     - prevTicks[CentralProcessor.TickType.IRQ.getIndex()]);
        long softIrq = Math.max(0, ticks[CentralProcessor.TickType.SOFTIRQ.getIndex()] - prevTicks[CentralProcessor.TickType.SOFTIRQ.getIndex()]);
        long steal   = Math.max(0, ticks[CentralProcessor.TickType.STEAL.getIndex()]   - prevTicks[CentralProcessor.TickType.STEAL.getIndex()]);
        long iowait  = Math.max(0, ticks[CentralProcessor.TickType.IOWAIT.getIndex()]  - prevTicks[CentralProcessor.TickType.IOWAIT.getIndex()]);
        long idle    = Math.max(0, ticks[CentralProcessor.TickType.IDLE.getIndex()]    - prevTicks[CentralProcessor.TickType.IDLE.getIndex()]);

        long total = user + nice + sys + irq + softIrq + steal + iowait + idle;
        if (total <= 0) return 0.0;

        long busy = total - idle - iowait;
        double usage = (double) busy / total;

        if (usage < 0) usage = 0;
        if (usage > 1) usage = 1;

        return usage;
    }


    public List<String> listNetworkInterfaceName() {
        HardwareAbstractionLayer hardware = info.getHardware();
        return hardware.getNetworkIFs()
                .stream()
                .map(NetworkIF::getName)
                .toList();
    }

    private String resolvePrimaryIp(HardwareAbstractionLayer hardware) {
        // 如果你有“优先网卡”的配置，可以先用原来的 findNetworkInterface(hardware)
        NetworkIF preferred = this.findNetworkInterface(hardware);
        String ip = pickValidIpFrom(preferred);
        if (ip != null) return ip;

        // 回退：扫描所有网卡，拿第一个有效地址
        for (NetworkIF nif : hardware.getNetworkIFs()) {
            String candidate = pickValidIpFrom(nif);
            if (candidate != null) return candidate;
        }
        return "unknown"; // 或者返回 "127.0.0.1" / 空串，按你的业务需要
    }

    private String pickValidIpFrom(NetworkIF nif) {
        if (nif == null) return null;
        try {
            // 刷新一次网卡信息，确保拿到最新地址
            nif.updateAttributes();
        } catch (Throwable ignore) {}

        String[] v4 = nif.getIPv4addr();
        if (v4 != null && v4.length > 0) {
            for (String s : v4) {
                if (isGoodIPv4(s)) return s;
            }
        }
        String[] v6 = nif.getIPv6addr();
        if (v6 != null && v6.length > 0) {
            for (String s : v6) {
                if (s != null && !s.isBlank()) return s;
            }
        }
        return null;
    }

    private boolean isGoodIPv4(String ip) {
        if (ip == null || ip.isBlank()) return false;
        if (ip.startsWith("127.")) return false;         // loopback
        if (ip.equals("0.0.0.0")) return false;          // 未分配
        if (ip.startsWith("169.254.")) return false;     // APIPA
        return true;
    }
}
