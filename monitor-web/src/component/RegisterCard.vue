<script setup>
import {useClipboard} from "@vueuse/core";
import {ElMessage} from "element-plus";

const props = defineProps({
  token: String,
  clientId: Number,
  form: Object,
  created: Boolean,
  serverAddress: String,
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['submit'])
const { copy } = useClipboard()

const copyText = text => {
  if(!text)
    return
  copy(text).then(() => ElMessage.success('Copied to clipboard'))
}
</script>

<template>
  <div class="register-card" v-loading="loading">
    <div class="title"><i class="fa-regular fa-square-plus"></i> Add New Host</div>
    <div class="desc">Create the host record first, then start the monitoring client whenever you are ready. The host will stay offline until the client connects.</div>
    <el-divider style="margin: 10px 0"/>
    <template v-if="!created">
      <div class="sub-title">1. Create Host Record</div>
      <div class="desc">The host will be added immediately. You do not need to start the client before creating it.</div>
      <div class="field-label">Host Name</div>
      <el-input v-model="form.name" maxlength="10" placeholder="For example: Web_01"/>
      <div class="field-label">Node Name</div>
      <el-input v-model="form.node" maxlength="10" placeholder="For example: Taipei"/>
      <div class="field-label">Location</div>
      <el-select v-model="form.location" style="width: 100%">
        <el-option value="cn" label="Mainland China"/>
        <el-option value="hk" label="Hong Kong"/>
        <el-option value="jp" label="Japan"/>
        <el-option value="us" label="United States"/>
        <el-option value="sg" label="Singapore"/>
        <el-option value="kr" label="South Korea"/>
        <el-option value="de" label="Germany"/>
      </el-select>
      <el-button class="action-button" type="primary" @click="emit('submit')">Create Host</el-button>
    </template>
    <template v-else>
      <div class="sub-title">1. Host Created</div>
      <div class="desc">Host ID: {{ clientId }}. It is already in the list and will turn online after the client connects.</div>
      <div class="sub-title" style="margin-top: 10px">2. Monitoring Server Address</div>
      <div class="desc">Fill this address into the monitoring client.</div>
      <div class="copy-row">
        <el-input :model-value="serverAddress" readonly/>
        <el-button @click="copyText(serverAddress)">Copy</el-button>
      </div>
      <div class="sub-title" style="margin-top: 10px">3. Authorization Token</div>
      <div class="desc">Use this token the first time the client connects.</div>
      <div class="copy-row">
        <el-input :model-value="token" readonly placeholder="Token will appear here once generated"/>
        <el-button @click="copyText(token)">Copy</el-button>
      </div>
      <div class="sub-title" style="margin-top: 10px">4. Start Client Later</div>
      <div class="desc">The monitored server only needs Java 17. You can start the client after this dialog is closed.</div>
    </template>
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

  .field-label {
    margin: 12px 0 6px;
    font-size: 13px;
    color: #606266;
  }

  .desc {
    font-size: 13px;
    color: grey;
    line-height: 16px;
  }

  .action-button {
    width: 100%;
    margin-top: 16px;
  }

  .copy-row {
    display: flex;
    gap: 10px;
  }
}
</style>
