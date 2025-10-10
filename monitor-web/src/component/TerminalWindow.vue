<script setup>
import {reactive, ref, watch} from "vue";
import {get, post} from "@/net";
import {ElMessage} from "element-plus";
import Terminal from "@/component/Terminal.vue";

const props = defineProps({
  id: Number
})

const connection = reactive({
  ip: '',
  port: 22,
  username: '',
  password: ''
})

const rules = {
  port: [
    { required: true, message: 'Please enter a port', trigger: ['blur', 'change'] },
  ],
  username: [
    { required: true, message: 'Please enter a username', trigger: ['blur', 'change'] },
  ],
  password: [
    { required: true, message: 'Please enter a password', trigger: ['blur', 'change'] },
  ]
}

const state = ref(1)
const formRef = ref()

function saveConnection() {
  formRef.value.validate((isValid) => {
    if(isValid) {
      post('/api/monitor/ssh-save', {
        ...connection,
        id: props.id
      }, () => state.value = 2)
    }
  })
}

watch(() => props.id, id => {
  state.value = 1
  if(id !== -1) {
    connection.ip = ''
    get(`/api/monitor/ssh?clientId=${id}`, data => Object.assign(connection, data))
  }
}, { immediate: true })
</script>

<template>
  <div class="terminal-main">
    <div class="login" v-loading="!connection.ip" v-if="state === 1">
      <i style="font-size: 50px" class="fa-solid fa-terminal"></i>
      <div style="margin-top: 10px;font-weight: bold;font-size: 20px">Server Connection Info</div>
      <el-form style="width: 400px;margin: 20px auto" :model="connection"
               :rules="rules" ref="formRef" label-width="100">
        <div style="display: flex;gap: 10px">
          <el-form-item style="width: 100%" label="Server IP Address" prop="ip">
            <el-input v-model="connection.ip" disabled/>
          </el-form-item>
          <el-form-item style="width: 80px" prop="port" label-width="0">
            <el-input placeholder="Port" v-model="connection.port"/>
          </el-form-item>
        </div>
        <el-form-item prop="username" label="Login Username">
          <el-input placeholder="Please enter a username..." v-model="connection.username"/>
        </el-form-item>
        <el-form-item prop="password" label="Login Password">
          <el-input placeholder="Please enter a password..." type="password" v-model="connection.password"/>
        </el-form-item>
        <el-button type="success" @click="saveConnection" plain>Connect Now</el-button>
      </el-form>
    </div>
    <div v-if="state === 2">
      <div style="overflow: hidden;padding: 0 10px 10px 10px">
        <terminal :id="id" @dispose="state = 1"/>
      </div>
    </div>
  </div>
</template>

<style scoped>
.terminal-main {
  width: 100%;
  height: 100%;

  .login {
    text-align: center;
    padding-top: 50px;
    height: 100%;
    box-sizing: border-box;
  }
}
</style>
