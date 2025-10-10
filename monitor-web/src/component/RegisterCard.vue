<script setup>
import axios from "axios";

defineProps({
  token: String
})
</script>

<template>
  <div class="register-card">
    <div class="title"><i class="fa-regular fa-square-plus"></i> Add New Host</div>
    <div class="desc">Follow the steps below to add a new host. After adding, you can manage the server in real time and monitor its status.</div>
    <el-divider style="margin: 10px 0"/>
    <div class="sub-title">1. Deploy Client</div>
    <div class="desc">Run the monitoring client on the server to be monitored. The client depends on Java 17; please install it in advance. After it starts, proceed to the next step.</div>
    <div class="sub-title" style="margin-top: 10px">2. Enter Monitoring Server Address</div>
    <div class="desc">This address is used by the client to report runtime status data in real time. Make sure it is filled in correctly.</div>
    <el-input v-model="axios.defaults.baseURL" readonly/>
    <div class="sub-title" style="margin-top: 10px">3. Enter Authorization Token</div>
    <div class="desc">All client requests must include the authorization token to be correctly recognized by the server.</div>
    <el-input :model-value="token" readonly/>
  </div>
</template>

<style scoped>
.register-card {
  margin: 15px 20px;

  .title {
    font-size: 18px;
    font-weight: bold;
  }

  .sub-title {
    font-size: 16px;
    font-weight: bold;
    color: dodgerblue;
  }

  .desc {
    font-size: 13px;
    color: grey;
    line-height: 16px;
  }
}
</style>
