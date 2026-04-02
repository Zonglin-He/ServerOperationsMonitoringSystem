package com.example.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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

    private String registerToken;

    private final Map<Integer, Client> clientIdCache = new ConcurrentHashMap<>();
    private final Map<String, Client> clientTokenCache = new ConcurrentHashMap<>();
    private final Object cacheLock = new Object();
    private volatile long cachedClientCount = 0;
    private volatile long lastCacheSyncTime = 0;

    @Resource private ClientDetailMapper detailMapper;
    @Resource private InfluxDbUtils influx;
    @Resource private ClientSshMapper sshMapper;

    private final Map<Integer, RuntimeDetailVO> currentRuntime = new ConcurrentHashMap<>();

    @PostConstruct
    public void initClientCache() {
        this.refreshClientCache();
        this.registerToken = this.generateAvailableToken();
    }

    @Override
    public RegisterClientVO createClient(CreateClientVO vo) {
        this.ensureClientCacheReady();
        Client client = new Client(
                this.generateAvailableClientId(),
                vo.getName(),
                this.generateAvailableToken(),
                vo.getLocation(),
                vo.getNode(),
                new Date()
        );
        if (!this.save(client)) {
            return null;
        }
        this.cacheClient(client);
        this.markCacheFresh();

        RegisterClientVO response = new RegisterClientVO();
        response.setId(client.getId());
        response.setName(client.getName());
        response.setNode(client.getNode());
        response.setLocation(client.getLocation());
        response.setToken(client.getToken());
        return response;
    }

    @Override
    public String registerToken() {
        return registerToken;
    }

    @Override
    public boolean verifyAndRegister(String token) {
        this.ensureClientCacheReady();
        if (clientTokenCache.containsKey(token)) {
            return true;
        }
        if (this.registerToken.equals(token)) {
            int id = this.generateAvailableClientId();
            Client client = new Client(id, "Unnamed device", token, "cn", "Unnamed node", new Date());
            if (this.save(client)) {
                registerToken = this.generateAvailableToken();
                this.cacheClient(client);
                this.markCacheFresh();
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

    /** 明细 UPSERT：按 client_id 查询最新一条，没有就 insert，有就基于 client_id 执行 update */
    @Override
    public void updateClientDetail(ClientDetailVO vo, Client client) {
        ClientDetail detail = new ClientDetail();
        BeanUtils.copyProperties(vo, detail);
        // 若实体属性名是 clientId，请确保有 @TableField("client_id") 映射
        try {
            ClientDetail.class.getMethod("setClientId", Integer.class).invoke(detail, client.getId());
        } catch (Exception ignore) { /* 列条件更新会兜底 */ }

        ClientDetail exist = detailMapper.selectOne(
                new QueryWrapper<ClientDetail>()
                        .eq("client_id", client.getId())
                        .orderByDesc("id")
                        .last("limit 1"));

        if (exist == null) {
            detailMapper.insert(detail);
        } else {
            detailMapper.update(detail, new UpdateWrapper<ClientDetail>().eq("client_id", client.getId()));
        }
    }

    @Override
    public void updateRuntimeDetail(RuntimeDetailVO vo, Client client) {
        RuntimeDetailVO old = currentRuntime.put(client.getId(), vo);
        if (old != null) {
            influx.writeRuntimeData(client.getId(), old);
        }
    }

    /** 列表：查库所有客户端 + 拼明细(最新一条) + 在线状态 + 当前使用率 */
    @Override
    public List<ClientPreviewVO> listClients() {
        this.ensureClientCacheReady();
        List<Client> clients = this.list();
        return clients.stream().map(client -> {
            ClientPreviewVO vo = client.asViewObject(ClientPreviewVO.class);

            ClientDetail detail = detailMapper.selectOne(
                    new QueryWrapper<ClientDetail>()
                            .eq("client_id", client.getId())
                            .orderByDesc("id")
                            .last("limit 1"));
            if (detail != null) {
                // ★ 不要覆盖 vo.id（客户端ID）
                BeanUtils.copyProperties(detail, vo, "id");
            }

            RuntimeDetailVO runtime = currentRuntime.get(client.getId());
            if (isOnline(runtime)) {
                BeanUtils.copyProperties(runtime, vo);
                vo.setOnline(true);
            } else {
                vo.setOnline(false);
            }
            return vo;
        }).toList();
    }

    @Override
    public void renameClient(RenameClientVO vo) {
        this.update(com.baomidou.mybatisplus.core.toolkit.Wrappers.<Client>update()
                .eq("id", vo.getId()).set("name", vo.getName()));
        this.refreshClientCache();
    }

    @Override
    public ClientDetailsVO clientDetails(int clientId) {
        this.ensureClientCacheReady();
        Client client = this.clientIdCache.get(clientId);
        if (client == null) client = this.getById(clientId);
        if (client == null) return null;

        ClientDetailsVO vo = client.asViewObject(ClientDetailsVO.class);

        ClientDetail detail = detailMapper.selectOne(
                new QueryWrapper<ClientDetail>()
                        .eq("client_id", clientId)
                        .orderByDesc("id")
                        .last("limit 1"));
        if (detail != null) {
            // ★ 不要覆盖 vo.id（客户端ID）
            BeanUtils.copyProperties(detail, vo, "id");
        }
        vo.setOnline(isOnline(currentRuntime.get(clientId)));
        return vo;
    }

    @Override
    public void renameNode(RenameNodeVO vo) {
        this.update(com.baomidou.mybatisplus.core.toolkit.Wrappers.<Client>update()
                .eq("id", vo.getId())
                .set("node", vo.getNode())
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
        ClientDetail detail = detailMapper.selectOne(
                new QueryWrapper<ClientDetail>()
                        .eq("client_id", clientId)
                        .orderByDesc("id")
                        .last("limit 1"));
        if (detail != null) {
            // ★ 防御性忽略 id（虽然 RuntimeHistoryVO 里通常没有 id）
            BeanUtils.copyProperties(detail, vo, "id");
        }
        return vo;
    }

    /** 删除：明细按 client_id 清理；SSH 表主键就是 id=clientId，直接 deleteById */
    @Override
    public void deleteClient(int clientId) {
        this.removeById(clientId);
        detailMapper.delete(new QueryWrapper<ClientDetail>().eq("client_id", clientId));
        sshMapper.deleteById(clientId);
        this.refreshClientCache();
        currentRuntime.remove(clientId);
    }

    /** 简要列表：查所有客户端 + 最新明细 */
    @Override
    public List<ClientSimpleVO> listSimpleList() {
        this.ensureClientCacheReady();
        List<Client> clients = this.list();
        return clients.stream().map(client -> {
            ClientSimpleVO vo = client.asViewObject(ClientSimpleVO.class);
            ClientDetail detail = detailMapper.selectOne(
                    new QueryWrapper<ClientDetail>()
                            .eq("client_id", client.getId())
                            .orderByDesc("id")
                            .last("limit 1"));
            if (detail != null) {
                // ★ 不要覆盖 vo.id（客户端ID）
                BeanUtils.copyProperties(detail, vo, "id");
            }
            return vo;
        }).toList();
    }

    /** SSH：你的实体主键就是 id -> 直接以 clientId 作为主键 UPSERT */
    @Override
    public void saveClientSshConnection(SshConnectionVO vo) {
        this.ensureClientCacheReady();
        Client client = clientIdCache.get(vo.getId());
        if (client == null) client = this.getById(vo.getId());
        if (client == null) return;

        ClientSsh ssh = new ClientSsh();
        BeanUtils.copyProperties(vo, ssh);
        ssh.setId(client.getId()); // 主键即 clientId

        if (sshMapper.selectById(client.getId()) != null) {
            sshMapper.updateById(ssh);
        } else {
            sshMapper.insert(ssh);
        }
    }

    @Override
    public SshSettingsVO sshSettings(int clientId) {
        ClientDetail detail = detailMapper.selectOne(
                new QueryWrapper<ClientDetail>()
                        .eq("client_id", clientId)
                        .orderByDesc("id")
                        .last("limit 1")
        );
        ClientSsh ssh = sshMapper.selectById(clientId);

        SshSettingsVO vo = (ssh == null) ? new SshSettingsVO() : ssh.asViewObject(SshSettingsVO.class);
        if (detail != null) {
            vo.setIp(detail.getIp());
        }
        return vo;
    }

    /* -------------------- helpers -------------------- */

    private boolean isOnline(RuntimeDetailVO runtime) {
        return runtime != null && System.currentTimeMillis() - runtime.getTimestamp() < 60 * 1000;
    }

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
        if (now - lastCacheSyncTime < 30_000) return;
        long dbCount = this.count();
        if (cachedClientCount != dbCount) this.refreshClientCache();
        else lastCacheSyncTime = now;
    }

    private void cacheClient(Client client) {
        clientIdCache.put(client.getId(), client);
        clientTokenCache.put(client.getToken(), client);
    }

    private void markCacheFresh() {
        cachedClientCount = clientIdCache.size();
        lastCacheSyncTime = System.currentTimeMillis();
    }

    private int generateAvailableClientId() {
        int id;
        do {
            id = new Random().nextInt(90000000) + 10000000;
        } while (clientIdCache.containsKey(id) || this.getById(id) != null);
        return id;
    }

    private String generateAvailableToken() {
        String token;
        do {
            token = this.generateNewToken();
        } while (clientTokenCache.containsKey(token) || Objects.equals(token, registerToken));
        return token;
    }

    private String generateNewToken() {
        String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(24);
        for (int i = 0; i < 24; i++) sb.append(CHARS.charAt(r.nextInt(CHARS.length())));
        return sb.toString();
    }
}
