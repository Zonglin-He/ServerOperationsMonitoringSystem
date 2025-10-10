<script setup>
import {reactive, ref} from "vue";
import {Lock, Message, User} from "@element-plus/icons-vue";
import {osNameToIcon} from "@/tools";
import {ElMessage} from "element-plus";
import {post} from "@/net";

defineProps({
  clients: Array
})
const emits = defineEmits(['create'])

const form = reactive({
  username: '',
  email: '',
  password: '',
})
const formRef = ref()
const valid = ref(false)
const onValidate = (prop, isValid) => valid.value = isValid

const validateUsername = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('Please enter a username'))
  } else if(!/^[a-zA-Z0-9\u4e00-\u9fa5]+$/.test(value)){
    callback(new Error('Username cannot contain special characters; only Chinese/English letters and digits are allowed'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: 'Please enter a username', trigger: ['blur', 'change'] },
    { validator: validateUsername, trigger: ['blur', 'change'] },
    { min: 2, max: 8, message: 'Username length must be between 2–8 characters', trigger: ['blur', 'change'] },
  ],
  password: [
    { required: true, message: 'Please enter a password', trigger: ['blur', 'change'] },
    { min: 6, max: 64, message: 'Password length must be between 6–64 characters', trigger: ['blur', 'change'] }
  ], email: [
    { required: true, message: 'Please enter an email address', trigger: ['blur', 'change'] },
    {type: 'email', message: 'Please enter a valid email address', trigger: ['blur', 'change']}
  ]
}

const checkedClients = []
const onCheck = (state, id) => {
  if(state) {
    checkedClients.push(id)
  } else {
    const index = checkedClients.indexOf(id);
    checkedClients.splice(index, 1)
  }
}

function createSubAccount() {
  formRef.value.validate(isValid => {
    if(checkedClients.length === 0) {
      ElMessage.warning('Please select at least one server for the sub-account to manage')
      return
    }
    if(isValid) {
      post('/api/user/sub/create', {
        ...form,
        clients: checkedClients
      }, () => {
        ElMessage.success('Sub-account created successfully!')
        emits('create')
      })
    }
  })
}
</script>

<template>
  <div style="padding: 15px 20px;height: 100%">
    <div style="display: flex;flex-direction: column;height: 100%">
      <div>
        <div class="title">
          <i class="fa-solid fa-user-plus"></i> Add New Sub-Account
        </div>
        <div class="desc">Sub-accounts also manage servers, but you can assign specific servers; sub-accounts can only access the servers assigned to them.</div>
        <el-divider style="margin: 10px 0"/>
      </div>
      <div>
        <el-form label-position="top" :rules="rules" :model="form"
                 @validate="onValidate" ref="formRef">
          <el-form-item label="Username" prop="username">
            <el-input type="text" v-model="form.username"
                      :prefix-icon="User" placeholder="Sub-account username" maxlength="16"/>
          </el-form-item>
          <el-form-item label="Email" prop="email">
            <el-input type="email" v-model="form.email"
                      :prefix-icon="Message" placeholder="Sub-account email" maxlength="16"/>
          </el-form-item>
          <el-form-item label="Password" prop="password">
            <el-input type="password" v-model="form.password"
                      :prefix-icon="Lock" placeholder="Sub-account password" maxlength="64"/>
          </el-form-item>
        </el-form>
        <el-divider style="margin: 10px 0"/>
        <div class="desc">Please select below the list of servers the sub-account is allowed to access.</div>
      </div>
      <el-scrollbar style="flex: 1">
        <div class="client-card" v-for="item in clients">
          <el-checkbox @change="state => onCheck(state, item.id)"/>
          <div style="margin-left: 20px">
            <div style="font-size: 14px;font-weight: bold">
              <span :class="`flag-icon flag-icon-${item.location}`"></span>
              <span style="margin: 0 10px">{{ item.name }}</span>
            </div>
            <div style="font-size: 12px;color: grey">
              OS:
              <i :style="{color: osNameToIcon(item.osName).color}"
                 :class="`fa-brands ${osNameToIcon(item.osName).icon}`"></i>
              {{`${item.osName} ${item.osVersion}`}}
            </div>
            <div style="font-size: 12px;color: grey">
              <span style="margin-right: 10px">Public IP: {{item.ip}}</span>
            </div>
          </div>
        </div>
      </el-scrollbar>
      <div style="text-align: center;margin-top: 10px">
        <el-button @click="createSubAccount" type="success"
                   :disabled="!valid" plain>Create</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.title {
  font-size: 18px;
  font-weight: bold;
  color: dodgerblue;
}

.desc {
  font-size: 13px;
  color: grey;
  line-height: 16px;
}

.client-card {
  border-radius: 5px;
  background-color: var(--el-bg-color-page);
  padding: 10px;
  display: flex;
  align-items: center;
  margin: 10px;
}
</style>
