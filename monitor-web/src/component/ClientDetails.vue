<script setup>
import {computed, reactive, watch} from "vue";
import {get, post} from "@/net";
import {copyIp, cpuNameToImage, fitByUnit, locationToFlagClass, osNameToIcon, percentageToStatus, rename} from "@/tools";
import {ElMessage, ElMessageBox} from "element-plus";
import RuntimeHistory from "@/component/RuntimeHistory.vue";
import {Connection, Delete} from "@element-plus/icons-vue";

const locations = [
  {name: 'cn', desc: 'Mainland China'},
  {name: 'hk', desc: 'Hong Kong'},
  {name: 'jp', desc: 'Japan'},
  {name: 'us', desc: 'United States'},
  {name: 'sg', desc: 'Singapore'},
  {name: 'kr', desc: 'South Korea'},
  {name: 'de', desc: 'Germany'}
]

const props = defineProps({
  id: Number,
  update: Function
})
const emits = defineEmits(['delete', 'terminal'])

const details = reactive({
  base: {},
  runtime: {
    list: []
  },
  editNode: false
})
const nodeEdit = reactive({
  name: '',
  location: ''
})
const enableNodeEdit = () => {
  details.editNode = true
  nodeEdit.name = details.base.node
  nodeEdit.location = details.base.location
}
const submitNodeEdit = () => {
  post('/api/monitor/node', {
    id: props.id,
    node: nodeEdit.name,
    location: nodeEdit.location
  }, () => {
    details.editNode = false
    updateDetails()
    ElMessage.success('Node info updated')
  })
}

function deleteClient() {
  ElMessageBox.confirm('After deleting this host, all statistics will be lost. Are you sure?', 'Delete Host', {
    confirmButtonText: 'Confirm',
    cancelButtonText: 'Cancel',
    type: 'warning',
  }).then(() => {
    get(`/api/monitor/delete?clientId=${props.id}`, () => {
      emits('delete')
      props.update()
      ElMessage.success('Host removed successfully')
    })
  }).catch(() => {})
}

function updateDetails() {
  props.update()
  init(props.id)
}

setInterval(() => {
  if(props.id !== -1 && details.runtime) {
    get(`/api/monitor/runtime-now?clientId=${props.id}`, data => {
      if(details.runtime.list.length >= 360)
        details.runtime.list.splice(0, 1)
      details.runtime.list.push(data)
    })
  }
}, 10000)

const now = computed(() => details.runtime.list[details.runtime.list.length - 1])
const osName = computed(() => details.base?.osName || '')
const osVersion = computed(() => details.base?.osVersion || '')
const osIcon = computed(() => osNameToIcon(osName.value))
const osDisplay = computed(() => {
  const parts = [osName.value, osVersion.value].filter(Boolean)
  return parts.length ? parts.join(' ') : 'Unknown'
})
const baseLocationClass = computed(() => locationToFlagClass(details.base?.location))
const memoryTotal = computed(() => {
  if(details.runtime?.memory)
    return details.runtime.memory
  if(details.base?.memory)
    return details.base.memory
  return 0
})
const diskTotal = computed(() => {
  if(details.runtime?.disk)
    return details.runtime.disk
  if(details.base?.disk)
    return details.base.disk
  return 0
})
const memoryUsage = computed(() => now.value?.memoryUsage ?? 0)
const diskUsage = computed(() => now.value?.diskUsage ?? 0)
const memoryPercentage = computed(() => {
  if(!memoryTotal.value)
    return 0
  const percent = memoryUsage.value / memoryTotal.value * 100
  return Math.min(Math.max(percent, 0), 100)
})
const diskPercentage = computed(() => {
  if(!diskTotal.value)
    return 0
  const percent = diskUsage.value / diskTotal.value * 100
  return Math.min(Math.max(percent, 0), 100)
})
const memoryUsageLabel = computed(() => {
  const usage = memoryUsage.value.toFixed(1)
  if(!memoryTotal.value)
    return `${usage} GB`
  return `${usage} GB / ${memoryTotal.value.toFixed(1)} GB`
})
const diskUsageLabel = computed(() => {
  const usage = diskUsage.value.toFixed(1)
  if(!diskTotal.value)
    return `${usage} GB`
  return `${usage} GB / ${diskTotal.value.toFixed(1)} GB`
})

const init = id => {
  if(id !== -1) {
    details.base = {}
    details.runtime = { list: [] }
    get(`/api/monitor/details?clientId=${id}`, data => Object.assign(details.base, data))
    get(`/api/monitor/runtime-history?clientId=${id}`, data => Object.assign(details.runtime, data))
  }
}
watch(() => props.id, init, { immediate: true })
</script>

<template>
  <el-scrollbar>
    <div class="client-details" v-loading="Object.keys(details.base).length === 0">
      <div v-if="Object.keys(details.base).length">
        <div style="display: flex;justify-content: space-between">
          <div class="title">
            <i class="fa-solid fa-server"></i>
            Server Info
          </div>
          <div>
            <el-button :icon="Connection" type="info"
                       @click="emits('terminal', id)" plain text>SSH Remote Connection</el-button>
            <el-button :icon="Delete" type="danger" style="margin-left: 0"
                       @click="deleteClient" plain text>Delete Host</el-button>
          </div>
        </div>
        <el-divider style="margin: 10px 0"/>
        <div class="details-list">
          <div>
            <span>Server ID</span>
            <span>{{details.base.id}}</span>
          </div>
          <div>
            <span>Server Name</span>
            <span>{{details.base.name}}</span>&nbsp;
            <i @click.stop="rename(details.base.id, details.base.name, updateDetails)"
               class="fa-solid fa-pen-to-square interact-item"/>
          </div>
          <div>
            <span>Status</span>
            <span>
            <i style="color: #18cb18" class="fa-solid fa-circle-play" v-if="details.base.online"></i>
            <i style="color: #18cb18" class="fa-solid fa-circle-stop" v-else></i>
            {{details.base.online ? 'Online' : 'Offline'}}
          </span>
          </div>
          <div v-if="!details.editNode">
            <span>Server Node</span>
            <span :class="baseLocationClass"></span>&nbsp;
            <span>{{details.base.node}}</span>&nbsp;
            <i @click.stop="enableNodeEdit"
               class="fa-solid fa-pen-to-square interact-item"/>
          </div>
          <div v-else>
            <span>Server Node</span>
            <div style="display: inline-block;height: 15px">
              <div style="display: flex">
                <el-select v-model="nodeEdit.location" style="width: 80px" size="small">
                  <el-option v-for="item in locations" :value="item.name">
                    <span :class="locationToFlagClass(item.name)"></span>&nbsp;
                    {{item.desc}}
                  </el-option>
                </el-select>
                <el-input v-model="nodeEdit.name" style="margin-left: 10px"
                          size="small" placeholder="Please enter node name..."/>
                <div style="margin-left: 10px">
                  <i @click.stop="submitNodeEdit" class="fa-solid fa-check interact-item"/>
                </div>
              </div>
            </div>
          </div>
          <div>
            <span>Public IP Address</span>
            <span>
            {{details.base.ip}}
            <i class="fa-solid fa-copy interact-item" style="color: dodgerblue" @click.stop="copyIp(details.base.ip)"></i>
          </span>
          </div>
          <div style="display: flex">
            <span>Processor</span>
            <span>{{details.base.cpuName}}</span>
            <el-image style="height: 20px;margin-left: 10px"
                      :src="`/cpu-icons/${cpuNameToImage(details.base.cpuName)}`"/>
          </div>
          <div>
            <span>Hardware Configuration</span>
            <span>
            <i class="fa-solid fa-microchip"></i>
            <span style="margin-right: 10px">{{` ${details.base.cpuCore} CPU Cores /`}}</span>
            <i class="fa-solid fa-memory"></i>
            <span>{{` ${memoryTotal.toFixed(1)} GB Memory Capacity`}}</span>
            <i class="fa-solid fa-hard-drive" style="margin-left: 10px"></i>
            <span>{{` ${diskTotal.toFixed(1)} GB Disk Capacity`}}</span>
          </span>
          </div>
          <div>
            <span>Operating System</span>
            <i :style="{color: osIcon.color}"
               :class="`fa-brands ${osIcon.icon}`"></i>
            <span style="margin-left: 10px">{{ osDisplay }}</span>
          </div>
        </div>
        <div class="title" style="margin-top: 20px">
          <i class="fa-solid fa-gauge-high"></i>
          Real-time Monitoring
        </div>
        <el-divider style="margin: 10px 0"/>
        <div v-if="details.base.online" v-loading="!details.runtime.list.length"
             style="min-height: 200px">
          <div style="display: flex" v-if="details.runtime.list.length">
            <el-progress type="dashboard" :width="100" :percentage="now.cpuUsage * 100"
                         :status="percentageToStatus(now.cpuUsage * 100)">
              <div style="font-size: 17px;font-weight: bold;color: initial">CPU</div>
              <div style="font-size: 13px;color: grey;margin-top: 5px">{{ (now.cpuUsage * 100).toFixed(1) }}%</div>
            </el-progress>
            <el-progress style="margin-left: 20px" type="dashboard" :width="100"
                         :percentage="memoryPercentage"
                         :status="percentageToStatus(memoryPercentage)">
              <div style="font-size: 16px;font-weight: bold;color: initial">Memory</div>
              <div style="font-size: 13px;color: grey;margin-top: 5px">{{ memoryUsageLabel }}</div>
            </el-progress>
            <div style="flex: 1;margin-left: 30px;display: flex;flex-direction: column;height: 80px">
              <div style="flex: 1;font-size: 14px">
                <div>Real-time Network Speed</div>
                <div>
                  <i style="color: orange" class="fa-solid fa-arrow-up"></i>
                  <span>{{` ${fitByUnit(now.networkUpload, 'KB')}/s`}}</span>
                  <el-divider direction="vertical"/>
                  <i style="color: dodgerblue" class="fa-solid fa-arrow-down"></i>
                  <span>{{` ${fitByUnit(now.networkDownload, 'KB')}/s`}}</span>
                </div>
              </div>
              <div>
                <div style="font-size: 13px;display: flex;justify-content: space-between">
                  <div>
                    <i class="fa-solid fa-hard-drive"></i>
                    <span> Total Disk Capacity</span>
                  </div>
                  <div>{{ diskUsageLabel }}</div>
                </div>
                <el-progress type="line" :show-text="false"
                             :status="percentageToStatus(diskPercentage)"
                             :percentage="diskPercentage" />
              </div>
            </div>
          </div>
          <runtime-history style="margin-top: 20px" :data="details.runtime.list"/>
        </div>
        <el-empty description="Server is offline. Please check if it is running properly." v-else/>
      </div>
    </div>
  </el-scrollbar>
</template>

<style scoped>
.interact-item {
  transition: .3s;

  &:hover {
    cursor: pointer;
    scale: 1.1;
    opacity: 0.8;
  }
}

.client-details {
  height: 100%;
  padding: 20px;

  .title {
    color: dodgerblue;
    font-size: 18px;
    font-weight: bold;
  }

  .details-list {
    font-size: 14px;

    & div {
      margin-bottom: 10px;

      & span:first-child {
        color: gray;
        font-size: 13px;
        font-weight: normal;
        width: 120px;
        display: inline-block;
      }

      & span {
        font-weight: bold;
      }
    }
  }
}
</style>
