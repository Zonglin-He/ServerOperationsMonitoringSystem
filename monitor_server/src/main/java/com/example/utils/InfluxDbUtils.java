package com.example.utils;

import com.alibaba.fastjson2.JSONObject;
import com.example.entity.dto.RuntimeData;
import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.response.RuntimeHistoryVO;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
public class InfluxDbUtils {

    @Value("${influx.url}")   private String influxUrl;
    @Value("${influx.token}") private String token;
    @Value("${influx.org}")   private String org;
    @Value("${influx.bucket}")private String bucket;

    private InfluxDBClient client;

    @PostConstruct
    public void init() {
        // 用配置中的 org/bucket 作为默认上下文
        client = InfluxDBClientFactory.create(influxUrl, token.toCharArray(), org, bucket);
    }

    @PreDestroy
    public void close() {
        if (client != null) client.close();
    }

    /** 写入一条运行时数据 */
    public void writeRuntimeData(int clientId, RuntimeDetailVO vo) {
        RuntimeData data = new RuntimeData();
        BeanUtils.copyProperties(vo, data);
        data.setTimestamp(new Date(vo.getTimestamp()).toInstant());
        data.setClientId(clientId);

        WriteApiBlocking write = client.getWriteApiBlocking();
        // 已经在 client 里绑定了 org/bucket，这里直接用默认重载即可
        write.writeMeasurement(WritePrecision.NS, data);
        // 如果你想显式指定，也可以：write.writeMeasurement(bucket, org, WritePrecision.NS, data);
    }

    /** 读取最近1小时数据（按字段 pivot 成一张表） */
    public RuntimeHistoryVO readRuntimeData(int clientId) {
        RuntimeHistoryVO vo = new RuntimeHistoryVO();

        String flux = """
                from(bucket: "%s")
                |> range(start: -1h)
                |> filter(fn: (r) => r._measurement == "runtime")
                |> filter(fn: (r) => r.clientId == "%s")
                |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                """.formatted(bucket, clientId);

        List<FluxTable> tables = client.getQueryApi().query(flux, org);
        if (tables.isEmpty()) return vo;

        for (FluxRecord r : tables.get(0).getRecords()) {
            JSONObject o = new JSONObject();
            o.put("timestamp", r.getTime());
            // 这里的 key 是各字段名（例如 cpu、mem、disk 等）
            r.getValues().forEach((k, v) -> {
                if (!k.startsWith("_") && !"result".equals(k) && !"table".equals(k) && !"clientId".equals(k))
                    o.put(k, v);
            });
            // 如果要把 clientId 也放回去：
            o.put("clientId", r.getValueByKey("clientId"));
            vo.getList().add(o);
        }
        return vo;
    }
}
