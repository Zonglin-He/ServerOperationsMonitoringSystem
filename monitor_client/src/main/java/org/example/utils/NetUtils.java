package org.example.utils;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.BaseDetail;
import org.example.entity.ConnectionConfig;
import org.example.entity.Response;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Component
public class NetUtils {
    private final HttpClient client = HttpClient.newHttpClient();

    @Lazy
    @Resource
    ConnectionConfig config;


    public boolean registerToServer(String address, String token) {
      log.info("Registering to server, please wait...");
      Response response = this.doGet("/register", address, token);
      if (response.success()){
          log.info("Registered successfully");
      }
      else{
          log.error("Register failed : {}", response.message());
      }
      return response.success();
    }

    public void updateBaseDetails(BaseDetail detail){
        Response response = this.doPost("/detail", detail);
        if (response.success()){
            log.info("Update base details successfully");
        }
        else{
            log.error("Update base details failed : {}", response.message());
        }
    }

    private Response doGet(String url, String address, String token) {
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(address + "/monitor" + url))
                    .header("Authorization", token)
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return JSONObject.parseObject(response.body(), Response.class);
        }catch (Exception e){
            log.error("Problem when requesting to server", e);
            return Response.errorResponse(e);
        }
    }

    private Response doGet(String url) {
        return this.doGet(url, config.getAddress(), config.getToken());
    }

    private Response doPost(String url, Object data) {
        try{
           String rawData = JSONObject.toJSONString(data);
           HttpRequest request = HttpRequest.newBuilder()
                   .POST(HttpRequest.BodyPublishers.ofString(rawData))
                   .uri(new URI(config.getAddress() + "/monitor" + url))
                   .header("Authorization", config.getToken())
                   .header("Content-Type", "application/json")
                   .build();
           HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
           return JSONObject.parseObject(response.body(), Response.class);
        }catch (Exception e){
            log.error("Problem when requesting to server", e);
            return Response.errorResponse(e);
        }
    }
}
