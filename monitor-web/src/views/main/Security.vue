<script setup>
import {reactive, ref} from "vue";
import {Delete, Lock, Plus, Refresh, Switch} from "@element-plus/icons-vue";
import {get, logout, post} from "@/net";
import {ElMessage} from "element-plus";
import router from "@/router";
import CreateSubAccount from "@/component/CreateSubAccount.vue";
import {useStore} from "@/store";

const store = useStore()

const formRef = ref()
const valid = ref(false)
const onValidate = (prop, isValid) => valid.value = isValid

const form = reactive({
  password: '',
  new_password: '',
  new_password_repeat: '',
})

const validatePassword = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('Please re-enter the password'))
  } else if (value !== form.new_password) {
    callback(new Error("The two passwords do not match"))
  } else {
    callback()
  }
}

const emailForm = reactive({
  email: store.user.email,
  code: ''
})

const coldTime = ref(0)
const isEmailValid = ref(true)

const onEmailValidate = (prop, isValid) => {
  if(prop === 'email')
    isEmailValid.value = isValid
}

const validateEmail = () => {
  coldTime.value = 60
  let handle;
  get(`/api/auth/ask-code?email=${emailForm.email}&type=modify`, () => {
    ElMessage.success(`Verification code sent to: ${emailForm.email}. Please check your inbox.`)
    handle = setInterval(() => {
      coldTime.value--
      if(coldTime.value === 0) {
        clearInterval(handle)
      }
    }, 1000)
  }, (message) => {
    ElMessage.warning(message)
    coldTime.value = 0
  })
}

function modifyEmail() {
  post('/api/user/modify-email', emailForm, () => {
    ElMessage.success('Email updated successfully')
    logout(() => router.push('/'))
  })
}

const rules = {
  password: [
    { required: true, message: 'Please enter your current password', trigger: 'blur' },
  ],
  new_password: [
    { required: true, message: 'Please enter a new password', trigger: 'blur' },
    { min: 6, max: 64, message: 'Password length must be 6–64 characters', trigger: ['blur'] }
  ],
  new_password_repeat: [
    { required: true, message: 'Please re-enter the new password', trigger: 'blur' },
    { validator: validatePassword, trigger: ['blur', 'change'] },
  ],
  email: [
    { required: true, message: 'Please enter an email address', trigger: 'blur' },
    {type: 'email', message: 'Please enter a valid email address', trigger: ['blur', 'change']}
  ]
}

function resetPassword() {
  formRef.value.validate(isValid => {
    if(isValid) {
      post('/api/user/change-password', form, () => {
        ElMessage.success('Password changed successfully. Please log in again!')
        logout(() => router.push('/'))
      })
    }
  })
}

const simpleList = ref([])
if(store.isAdmin) {
  get('/api/monitor/simple-list', list => {
    simpleList.value = list
    initSubAccounts()
  })
}

const accounts = ref([])
const initSubAccounts = () =>
    get('/api/user/sub/list', list => accounts.value = list)

const createAccount = ref(false)

function deleteAccount(id) {
  get(`/api/user/sub/delete?uid=${id}`, () => {
    ElMessage.success('Sub-account deleted successfully')
    initSubAccounts()
  })
}
</script>

<template>
  <div style="display: flex;gap: 10px">
    <div style="flex: 50%">
      <div class="info-card">
        <div class="title"><i class="fa-solid fa-lock"></i> Change Password</div>
        <el-divider style="margin: 10px 0"/>
        <el-form @validate="onValidate" :model="form" :rules="rules"
                 ref="formRef" style="margin: 20px" label-width="100">
          <el-form-item label="Current Password" prop="password">
            <el-input type="password" v-model="form.password"
                      :prefix-icon="Lock" placeholder="Current password" maxlength="64"/>
          </el-form-item>
          <el-form-item label="New Password" prop="new_password">
            <el-input type="password" v-model="form.new_password"
                      :prefix-icon="Lock" placeholder="New password" maxlength="64"/>
          </el-form-item>
          <el-form-item label="Repeat New Password" prop="new_password_repeat">
            <el-input type="password" v-model="form.new_password_repeat"
                      :prefix-icon="Lock" placeholder="Repeat new password" maxlength="64"/>
          </el-form-item>
          <div style="text-align: center">
            <el-button :icon="Switch" @click="resetPassword"
                       type="success" :disabled="!valid">Reset Password Now</el-button>
          </div>
        </el-form>
      </div>
      <div class="info-card" style="margin-top: 10px">
        <div class="title"><i class="fa-regular fa-envelope"></i> Email Settings</div>
        <el-divider style="margin: 10px 0"/>
        <el-form :model="emailForm" label-position="top" :rules="rules"
                 ref="emailFormRef" @validate="onEmailValidate" style="margin: 0 10px 10px 10px">
          <el-form-item label="Email" prop="email">
            <el-input v-model="emailForm.email"/>
          </el-form-item>
          <el-form-item>
            <el-row style="width: 100%" :gutter="10">
              <el-col :span="18">
                <el-input placeholder="Enter verification code" v-model="emailForm.code"/>
              </el-col>
              <el-col :span="6">
                <el-button type="success" @click="validateEmail" style="width: 100%;"
                           :disabled="!isEmailValid || coldTime > 0">
                  {{coldTime > 0 ? 'Please wait ' + coldTime + ' s' : 'Get Code'}}
                </el-button>
              </el-col>
            </el-row>
          </el-form-item>
          <div>
            <el-button @click="modifyEmail" :disabled="!emailForm.email"
                       :icon="Refresh" type="success">Save Email</el-button>
          </div>
        </el-form>
      </div>
    </div>
    <div class="info-card" style="flex: 50%">
      <div class="title"><i class="fa-solid fa-users"></i> Sub-user Management</div>
      <el-divider style="margin: 10px 0"/>
      <div v-if="accounts.length" style="text-align: center">
        <div v-for="item in accounts" class="account-card">
          <el-avatar class="avatar" :size="30"
                     src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png"/>
          <div style="margin-left: 15px;line-height: 18px;flex: 1">
            <div>
              <span>{{item.username}}</span>
              <span style="font-size: 13px;color: grey;margin-left: 5px">
                Managing {{item.clientList.length}} servers
              </span>
            </div>
            <div style="font-size: 13px;color: grey">{{item.email}}</div>
          </div>
          <el-button type="danger" :icon="Delete"
                     @click="deleteAccount(item.id)" text>Delete Sub-Account</el-button>
        </div>
        <el-button :icon="Plus" type="primary"
                   @click="createAccount = true" plain>Add More Sub-Users</el-button>
      </div>
      <div v-else>
        <el-empty :image-size="100" description="No sub-users yet" v-if="store.isAdmin">
          <el-button :icon="Plus" type="primary" plain
                     @click="createAccount = true">Add Sub-User</el-button>
        </el-empty>
        <el-empty :image-size="100" description="Sub-accounts can only be managed by an administrator account" v-else/>
      </div>
    </div>
    <el-drawer v-model="createAccount" size="350" :with-header="false">
      <create-sub-account :clients="simpleList" @create="createAccount = false;initSubAccounts()"/>
    </el-drawer>
  </div>
</template>

<style scoped>
.info-card {
  border-radius: 7px;
  padding: 15px 20px;
  background-color: var(--el-bg-color);
  height: fit-content;

  .title {
    font-size: 18px;
    font-weight: bold;
    color: dodgerblue;
  }
}

.account-card {
  border-radius: 5px;
  background-color: var(--el-bg-color-page);
  padding: 10px;
  display: flex;
  align-items: center;
  text-align: left;
  margin: 10px 0;
}

:deep(.el-drawer) {
  margin: 10px;
  height: calc(100% - 20px);
  border-radius: 10px;
}

:deep(.el-drawer__body) {
  padding: 0;
}
</style>
