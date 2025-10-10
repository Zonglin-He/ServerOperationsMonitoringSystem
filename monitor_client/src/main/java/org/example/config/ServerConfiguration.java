package org.example.config;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.ConnectionConfig;
import org.example.utils.MonitorUtils;
import org.example.utils.NetUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

@Slf4j
@Configuration
public class ServerConfiguration implements ApplicationRunner {

    @Resource
    NetUtils net;

    @Resource
    MonitorUtils monitor;

    @Bean
    ConnectionConfig connectionConfig() {
        log.info("Loading client connection configuration...");
        ConnectionConfig config = this.readConfigurationFromFile();
        if (config == null){
            config = this.registerToServer();
        }
        return config;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Updating basic information to server...");
        net.updateBaseDetails(monitor.monitorBaseDetail());
    }

    private ConnectionConfig registerToServer() {
        Scanner scanner = new Scanner(System.in);
        String token, address, ifName;
        do {
            log.info("Please input server address:");
            address = scanner.nextLine();
            log.info("Please input token:");
            token = scanner.nextLine();
            List<String> ifs = monitor.listNetworkInterfaceName();
            log.info("Detected NICs (with IPv4 only): {}", ifs);
            if (ifs.size() > 1) {
                do {
                    log.info("Choose the device need to be monitored (one of {}):", ifs);
                    ifName = scanner.nextLine().trim();
                } while (!ifs.contains(ifName));
            } else if (ifs.size() == 1) {
                ifName = ifs.get(0);
                log.info("Only one NIC found, use: {}", ifName);
            } else {
                throw new IllegalStateException("No NIC with IPv4 found");
            }

        } while(!net.registerToServer(address, token));
        ConnectionConfig config = new ConnectionConfig(address, token, ifName);
        this.saveConfigurationToFile(config);
        return config;
    }

    private void saveConfigurationToFile(ConnectionConfig config) {
        File dir = new File("config");
        if (!dir.exists() && dir.mkdir()){
            log.info("Create config directory successfully");
        }
        File file = new File("config/server.json");
        try(FileWriter writer = new FileWriter(file)){
            writer.write(JSONObject.toJSONString(config));
        }catch (IOException e){
            log.error("Failed to save configuration file", e);
        }
        log.info("Connection of server has been saved successfully");
    }

    private ConnectionConfig readConfigurationFromFile() {
        File configurationFile = new File("config/server.json");
        if (configurationFile.exists()){
            try (FileInputStream stream = new FileInputStream(configurationFile)){
                String raw = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                return JSONObject.parseObject(raw).to(ConnectionConfig.class);
            }catch (IOException e){
                log.error("Failed to load configuration file", e);
            }
        }
        return null;
    }
}
