package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.dto.Client;
import com.example.entity.dto.ClientDetail;
import com.example.entity.dto.ClientSsh;
import com.example.entity.vo.request.*;
import com.example.entity.vo.response.*;
import com.example.mapper.ClientDetailMapper;
import com.example.mapper.ClientMapper;
import com.example.mapper.ClientSshMapper;
import com.example.service.ClientService;
import com.example.utils.InfluxDbUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ClientServiceImpl extends ServiceImpl<ClientMapper, Client> implements ClientService {

    private String registerToken = this.generateNewToken();

    private final Map<Integer, Client> clientIdCache = new ConcurrentHashMap<>();
    private final Map<String, Client> clientTokenCache = new ConcurrentHashMap<>();
    private final Object cacheLock = new Object();
    private volatile long cachedClientCount = 0;
    private volatile long lastCacheSyncTime = 0;

    @Resource
    ClientDetailMapper detailMapper;

    @Resource
    InfluxDbUtils influx;

    @Resource
    ClientSshMapper sshMapper;

    @PostConstruct
    public void initClientCache() {
        this.refreshClientCache();
    }

    @Override
    public String registerToken() {
        return registerToken;
    }

    @Override
    public boolean verifyAndRegister(String token) {
        if (this.registerToken.equals(token)){
            int id = this.randomClientId();
            System.out.println(id);
            Client client = new Client(id, "Unnamed device", token, "cn", "Unnamed node", new Date());
            if (this.save(client)){
                registerToken = this.generateNewToken();
                this.cacheClient(client);
                cachedClientCount = clientIdCache.size();
                lastCacheSyncTime = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }

    @Override
    public Client findClientById(int id) {
        this.ensureClientCacheReady();
        return clientIdCache.get(id);
    }

    @Override
    public Client findClientByToken(String token) {
        this.ensureClientCacheReady();
        return clientTokenCache.get(token);
    }

    @Override
    public void updateClientDetail(ClientDetailVO vo, Client client) {
        ClientDetail detail = new ClientDetail();
        BeanUtils.copyProperties(vo, detail);
        detail.setClientId(client.getId());
        if (detailMapper.selectById(client.getId()) != null){
            detailMapper.updateById(detail);
        } else {
            detailMapper.insert(detail);
        }
    }


    @Override
    public void updateRuntimeDetail(RuntimeDetailVO vo, Client client) {
        RuntimeDetailVO old = currentRuntime.put(client.getId(), vo);
        if (old!=null) {
            influx.writeRuntimeData(client.getId(), old);
        }
    }

    @Override
    public List<ClientPreviewVO> listClients() {
        this.ensureClientCacheReady();
        return clientIdCache.values().stream().map(client -> {
            ClientPreviewVO vo = client.asViewObject(ClientPreviewVO.class);
            ClientDetail detail = detailMapper.selectById(vo.getId());
            if (detail != null) {
                BeanUtils.copyProperties(detail, vo);
            }
            RuntimeDetailVO runtime = currentRuntime.get(client.getId());
            if (this.isOnline(runtime)) {
                BeanUtils.copyProperties(runtime, vo);
                vo.setOnline(true);
            }
            return vo;
        }).toList();
    }


    @Override
    public void renameClient(RenameClientVO vo) {
        this.update(Wrappers.<Client>update().eq("id", vo.getId()).set("name", vo.getName()));
        this.refreshClientCache();
    }

    @Override
    public ClientDetailsVO clientDetails(int clientId) {
        this.ensureClientCacheReady();
        Client client = this.clientIdCache.get(clientId);
        if (client == null) {
            return null;
        }
        ClientDetailsVO vo = client.asViewObject(ClientDetailsVO.class);
        ClientDetail detail = detailMapper.selectById(clientId);
        if (detail != null) {
            BeanUtils.copyProperties(detail, vo);
        }
        vo.setOnline(this.isOnline(currentRuntime.get(clientId)));
        return vo;
    }

    @Override
    public void renameNode(RenameNodeVO vo) {
        this.update(Wrappers.<Client>update().eq("id", vo.getId()).set("node", vo.getNode())
                .set("location", vo.getLocation()));
        this.refreshClientCache();
    }

    @Override
    public RuntimeDetailVO clientRuntimeDetailsNow(int clientId) {
        return currentRuntime.get(clientId);
    }

    @Override
    public RuntimeHistoryVO clientRuntimeDetailsHistory(int clientId) {
        RuntimeHistoryVO vo = influx.readRuntimeData(clientId);
        ClientDetail detail = detailMapper.selectById(clientId);
        if (detail != null) {
            BeanUtils.copyProperties(detail, vo);
        }
        return vo;
    }

    @Override
    public void deleteClient(int clientId) {
        this.removeById(clientId);
        detailMapper.deleteById(clientId);
        this.refreshClientCache();
        currentRuntime.remove(clientId);
    }

    @Override
    public List<ClientSimpleVO> listSimpleList() {
        this.ensureClientCacheReady();
        return clientIdCache.values().stream().map(client -> {
            ClientSimpleVO vo = client.asViewObject(ClientSimpleVO.class);
            ClientDetail detail = detailMapper.selectById(vo.getId());
            if (detail != null) {
                BeanUtils.copyProperties(detail, vo);
            }
            return vo;
        }).toList();
    }


    @Override
    public void saveClientSshConnection(SshConnectionVO vo) {
        this.ensureClientCacheReady();
        Client client = clientIdCache.get(vo.getId());
        if (client == null) {return;}
        ClientSsh ssh = new ClientSsh();
        BeanUtils.copyProperties(vo, ssh);
        if(Objects.nonNull(sshMapper.selectById(client.getId()))) {
            sshMapper.updateById(ssh);
        } else {
            sshMapper.insert(ssh);
        }
    }

    @Override
    public SshSettingsVO sshSettings(int clientId) {
        ClientDetail detail = detailMapper.selectById(clientId);
        ClientSsh ssh = sshMapper.selectById(clientId);
        SshSettingsVO vo;
        if(ssh == null) {
            vo = new SshSettingsVO();
        } else {
            vo = ssh.asViewObject(SshSettingsVO.class);
        }
        if (detail != null) {
            vo.setIp(detail.getIp());
        }
        return vo;
    }

    private boolean isOnline(RuntimeDetailVO runtime){
        return runtime != null && System.currentTimeMillis() - runtime.getTimestamp() < 60 * 1000;
    }

    private final Map<Integer, RuntimeDetailVO> currentRuntime = new ConcurrentHashMap<>();

    private void refreshClientCache() {
        synchronized (cacheLock) {
            clientTokenCache.clear();
            clientIdCache.clear();
            this.list().forEach(this::cacheClient);
            cachedClientCount = clientIdCache.size();
            lastCacheSyncTime = System.currentTimeMillis();
        }
    }

    private void ensureClientCacheReady() {
        if (clientIdCache.isEmpty()) {
            this.refreshClientCache();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCacheSyncTime < 30_000) {
            return;
        }
        long dbCount = this.count();
        if (cachedClientCount != dbCount) {
            this.refreshClientCache();
        } else {
            lastCacheSyncTime = now;
        }
    }

    private void cacheClient(Client client) {
        clientIdCache.put(client.getId(), client);
        clientTokenCache.put(client.getToken(), client);
    }

    private int randomClientId() {return new Random().nextInt(90000000) + 10000000;}

    private String generateNewToken() {
        String CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(24);
        for (int i = 0; i < 24; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}
