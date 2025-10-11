import {useClipboard} from "@vueuse/core";
import {ElMessage, ElMessageBox} from "element-plus";
import {post} from "@/net";

function fitByUnit(value, unit) {
    const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
    let index = units.indexOf(unit)
    while (((value < 1 && value !== 0) || value >= 1024) && (index >= 0 || index < units.length)) {
        if(value >= 1024) {
            value = value / 1024
            index = index + 1
        } else {
            value = value * 1024
            index = index - 1
        }
    }
    return `${parseInt(value)} ${units[index]}`
}

function percentageToStatus(percentage) {
    if(percentage < 50)
        return 'success'
    else if(percentage < 80)
        return 'warning'
    else
        return 'exception'
}

// 新包 flag-icons 的默认样式
const defaultOsIcon = { icon: 'fa-linux', color: 'grey' }
const defaultFlagClass = 'fi fi-xx'

// 支持的地区代码（2 位小写 ISO）
const supportedLocations = new Set(['cn', 'hk', 'jp', 'us', 'sg', 'kr', 'de'])

// 常见别名到 ISO 代码
const locationAlias = new Map([
    ['usa', 'us'],
    ['united states', 'us'],
    ['america', 'us'],
    ['sgp', 'sg'],
    ['singapore', 'sg']
])

// ✅ 统一生成 class，避免模板里手拼
export function flagClass(loc) {
    const raw = (loc || '').toString().trim().toLowerCase()
    const code = locationAlias.get(raw) || raw
    const iso2 = supportedLocations.has(code) ? code : 'xx'
    return `fi fi-${iso2}`           // 若要方形，改成：`fi fis-${iso2}`
}


function osNameToIcon(name) {
    if(!name)
        return defaultOsIcon
    if(name.indexOf('Ubuntu') >= 0)
        return {icon: 'fa-ubuntu', color: '#db4c1a'}
    else if(name.indexOf('CentOS') >= 0)
        return {icon: 'fa-centos', color: '#9dcd30'}
    else if(name.indexOf('macOS') >= 0)
        return {icon: 'fa-apple', color: '#D3D3D3'}
    else if(name.indexOf('Windows') >= 0)
        return {icon: 'fa-windows', color: '#3578b9'}
    else if(name.indexOf('Debian') >= 0)
        return {icon: 'fa-debian', color: '#a80836'}
    else
        return defaultOsIcon
}

function cpuNameToImage(name) {
    if(!name)
        return 'Intel.png'
    if(name.indexOf('Apple') >= 0)
        return 'Apple.png'
    else if(name.indexOf('AMD') >= 0)
        return 'AMD.png'
    else
        return 'Intel.png'
}

function resolveFlagCode(code) {
    const normalized = String(code).trim().toLowerCase()
    if(!normalized)
        return undefined

    if(supportedLocations.has(normalized))
        return normalized

    if(locationAlias.has(normalized))
        return locationAlias.get(normalized)

    const tokens = normalized.split(/[^a-z]/).filter(Boolean)
    for (const token of tokens) {
        if(supportedLocations.has(token))
            return token
        if(locationAlias.has(token))
            return locationAlias.get(token)
    }

    if(normalized.length === 3) {
        const iso2 = locationAlias.get(normalized)
        if(iso2)
            return iso2
    }

    if(normalized.length > 2) {
        const prefix = normalized.substring(0, 2)
        if(supportedLocations.has(prefix))
            return prefix
    }

    return undefined
}

function locationToFlagClass(code) {
    if (!code) return defaultFlagClass
    const result = resolveFlagCode(code)   // 返回类似 'us' 'cn' 的 2 位小写
    const iso2 = (result && supportedLocations.has(result)) ? result : 'xx'
    return `fi fi-${iso2}`                 // 方形想要的话用：`fi fis-${iso2}`
}

const { copy } = useClipboard()
const copyIp = ip => copy(ip).then(() => ElMessage.success('IP address copied to clipboard'))

function rename(id, name, after) {
    ElMessageBox.prompt('Please enter a new host name', 'Rename', {
        confirmButtonText: 'Confirm',
        cancelButtonText: 'Cancel',
        inputValue: name,
        inputPattern: /^[a-zA-Z0-9_\u4e00-\u9fa5]{1,10}$/,
        inputErrorMessage: 'Name can only contain letters, numbers, and underscores',
    }).then(({ value }) => post('/api/monitor/rename', {
            id: id,
            name: value
        }, () => {
            ElMessage.success('Host name updated')
            after()
        })
    )
}

export { fitByUnit, percentageToStatus, cpuNameToImage, osNameToIcon, rename, copyIp, locationToFlagClass }
