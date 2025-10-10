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

const defaultOsIcon = {icon: 'fa-linux', color: 'grey'}
const defaultFlagClass = 'flag-icon flag-icon-xx'
const supportedLocations = new Set(['cn', 'hk', 'jp', 'us', 'sg', 'kr', 'de'])
const locationAliases = new Map([
    ['america', 'us'],
    ['united states', 'us'],
    ['unitedstates', 'us'],
    ['usa', 'us'],
    ['us-east', 'us'],
    ['us-west', 'us'],
    ['singapore', 'sg'],
    ['sgp', 'sg'],
    ['sg-east', 'sg'],
    ['asia-sg', 'sg']
])

function osNameToIcon(name) {
    if(!name)
        return defaultOsIcon
    if(name.indexOf('Ubuntu') >= 0)
        return {icon: 'fa-ubuntu', color: '#db4c1a'}
    else if(name.indexOf('CentOS') >= 0)
        return {icon: 'fa-centos', color: '#9dcd30'}
    else if(name.indexOf('macOS') >= 0)
        return {icon: 'fa-apple', color: 'grey'}
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

function resolveLocationAlias(value) {
    if(locationAliases.has(value))
        return locationAliases.get(value)
    for(const [alias, target] of locationAliases.entries()) {
        if(value.includes(alias))
            return target
    }
    return undefined
}

function locationToFlagClass(code) {
    if(!code)
        return defaultFlagClass
    const normalized = String(code).trim().toLowerCase()
    if(supportedLocations.has(normalized))
        return `flag-icon flag-icon-${normalized}`
    const alias = resolveLocationAlias(normalized)
    if(alias)
        return `flag-icon flag-icon-${alias}`
    const parts = normalized.split(/[-_\s]+/)
    for(const part of parts) {
        if(supportedLocations.has(part))
            return `flag-icon flag-icon-${part}`
        const partAlias = resolveLocationAlias(part)
        if(partAlias)
            return `flag-icon flag-icon-${partAlias}`
    }
    const suffixMatch = normalized.match(/[a-z]{2}$/)
    if(suffixMatch && supportedLocations.has(suffixMatch[0]))
        return `flag-icon flag-icon-${suffixMatch[0]}`
    return defaultFlagClass
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
