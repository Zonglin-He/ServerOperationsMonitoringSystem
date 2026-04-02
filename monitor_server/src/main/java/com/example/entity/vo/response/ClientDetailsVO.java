package com.example.entity.vo.response;

import lombok.Data;

@Data
public class ClientDetailsVO {
    int id;
    String name;
    boolean online;
    String location;
    String node;
    String ip;
    String cpuName;
    String osName;
    String osVersion;
    double memory;
    int cpuCore;
    double disk;
}
