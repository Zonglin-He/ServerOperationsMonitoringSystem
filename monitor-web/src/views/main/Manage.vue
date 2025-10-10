<script setup>
import PreviewCard from "@/component/PreviewCard.vue";
import {computed, reactive, ref} from "vue";
import {get} from "@/net";
import ClientDetails from "@/component/ClientDetails.vue";
import RegisterCard from "@/component/RegisterCard.vue";
import {Plus} from "@element-plus/icons-vue";
import {useRoute} from "vue-router";
import {useStore} from "@/store";
import TerminalWindow from "@/component/TerminalWindow.vue";
import {ElMessage} from "element-plus";
import {locationToFlagClass} from "@/tools";

const locations = [
  {name: 'cn', desc: 'Mainland China'},
  {name: 'hk', desc: 'Hong Kong'},
  {name: 'jp', desc: 'Japan'},
  {name: 'us', desc: 'United States'},
  {name: 'sg', desc: 'Singapore'},
  {name: 'kr', desc: 'South Korea'},
  {name: 'de', desc: 'Germany'}
]
const checkedNodes = ref([])

const list = ref([])
const store = useStore()

const route = useRoute()

const updateList = () => {
  if(route.name === 'manage') {
    get('/api/monitor/list', data => list.value = data)
  }
}
setInterval(updateList, 10000)
updateList()

const detail = reactive({
  show: false,
  id: -1
})
const displayClientDetails = (id) => {
  detail.show = true
  detail.id = id
}

const clientList = computed(() => {
  if(checkedNodes.value.length === 0) {
    return list.value
  } else {
    return list.value.filter(item => checkedNodes.value.indexOf(item.location) >= 0)
  }
})

const register = reactive({
  show: false,
  token: '',
  loading: false
})

const refreshToken = () => {
  register.loading = true
  register.token = ''
  get('/api/monitor/register', token => {
    register.token = token
    register.loading = false
  }, (message, status, url) => {
    register.loading = false
    register.show = false
    ElMessage.warning(message)
    console.warn(`Request URL: ${url}, Status: ${status}, Message: ${message}`)
  })
}

const openRegisterDrawer = () => {
  if (!store.isAdmin) {
    ElMessage.warning('Only administrator accounts can register new hosts.')
    return
  }
  register.show = true
  refreshToken()
}

function openTerminal(id) {
  terminal.show = true
  terminal.id = id
  detail.show = false
}
const terminal = reactive({
  show: false,
  id: -1
})
</script>

<template>
  <div class="manage-main">
    <div style="display: flex;justify-content: space-between;align-items: end">
      <div>
        <div class="title"><i class="fa-solid fa-server"></i> Manage Hosts</div>
        <div class="desc">Manage all registered host instances here, monitor their status in real time, and operate quickly.</div>
      </div>
      <div>
        <el-button :icon="Plus" type="primary" plain :disabled="!store.isAdmin"
                   @click="openRegisterDrawer">Add New Host</el-button>
      </div>
    </div>
    <el-divider style="margin: 10px 0"/>
    <div style="margin-bottom: 20px">
      <el-checkbox-group v-model="checkedNodes">
        <el-checkbox v-for="node in locations" :key="node" :label="node.name" border>
          <span :class="locationToFlagClass(node.name)"></span>
          <span style="font-size: 13px;margin-left: 10px">{{node.desc}}</span>
        </el-checkbox>
      </el-checkbox-group>
    </div>
    <div class="card-list" v-if="list.length">
      <preview-card v-for="item in clientList" :data="item" :update="updateList"
                    @click="displayClientDetails(item.id)"/>
    </div>
    <el-empty description="No hosts yet. Click the top-right button to add one." v-else/>
    <el-drawer size="520" :show-close="false" v-model="detail.show"
               :with-header="false" v-if="list.length" @close="detail.id = -1">
      <client-details :id="detail.id" :update="updateList" @delete="updateList" @terminal="openTerminal"/>
    </el-drawer>
    <el-drawer v-model="register.show" direction="btt" :with-header="false"
               style="width: 600px;margin: 10px auto" size="320">
      <register-card :token="register.token" :loading="register.loading"/>
    </el-drawer>
    <el-drawer style="width: 800px" :size="520" direction="btt"
               @close="terminal.id = -1"
               v-model="terminal.show" :close-on-click-modal="false">
      <template #header>
        <div>
          <div style="font-size: 18px;color: dodgerblue;font-weight: bold;">SSH Remote Connection</div>
          <div style="font-size: 14px">
            The remote connection is established by the server, so it works inside private networks as well.
          </div>
        </div>
      </template>
      <terminal-window :id="terminal.id"/>
    </el-drawer>
  </div>
</template>

<style scoped>
:deep(.el-drawer__header) {
  margin-bottom: 10px;
}

:deep(.el-checkbox-group .el-checkbox) {
  margin-right: 10px;
}

:deep(.el-drawer) {
  margin: 10px;
  height: calc(100% - 20px);
  border-radius: 10px;
}

:deep(.el-drawer__body) {
  padding: 0;
}

.manage-main {
  margin: 0 50px;

  .title {
    font-size: 22px;
    font-weight: bold;
  }

  .desc {
    font-size: 15px;
    color: grey;
  }
}

.card-list {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}
</style>
