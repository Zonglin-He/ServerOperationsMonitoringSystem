import axios from "axios";
import router from "@/router";
import { ElMessage } from "element-plus";
import { useStore } from "@/store";

const authItemName = "authorize";

// 可选：设置后端基址（开发期也可使用 Vite 代理）
axios.defaults.baseURL = import.meta.env?.VITE_API_BASE || "";
axios.defaults.timeout = 15000;

// ====== token 存取 ======
function normalizeExpireMs(expire) {
    // 后端返回可能是字符串 "yyyy-MM-dd HH:mm:ss.SSS" 或 Date 序列化
    if (typeof expire === "number") return expire;
    const t = Date.parse(expire) || Date.parse(String(expire).replace(" ", "T"));
    return isNaN(t) ? Date.now() + 2 * 60 * 60 * 1000 : t; // 兜底给 2 小时
}

function takeAccessToken() {
    const str = localStorage.getItem(authItemName) || sessionStorage.getItem(authItemName);
    if (!str) return null;
    const authObj = JSON.parse(str);
    const expMs = typeof authObj.expire === "number" ? authObj.expire : normalizeExpireMs(authObj.expire);
    if (Date.now() >= expMs) {
        deleteAccessToken();
        ElMessage.warning("Login session has expired. Please sign in again!");
        return null;
    }
    return authObj.token;
}

function storeAccessToken(remember, token, expire) {
    const expireMs = normalizeExpireMs(expire);
    const authObj = { token, expire: expireMs };
    const str = JSON.stringify(authObj);
    if (remember) localStorage.setItem(authItemName, str);
    else sessionStorage.setItem(authItemName, str);
}

function deleteAccessToken() {
    localStorage.removeItem(authItemName);
    sessionStorage.removeItem(authItemName);
}

// 只有有 token 才加 Authorization；没有就别加，避免 "Bearer null"
const accessHeader = () => {
    const token = takeAccessToken()
    return token ? { Authorization: `Bearer ${token}` } : {}
}

// ====== 统一错误处理 ======
const defaultFailure = (message, status, url) => {
    console.warn(`Request URL: ${url}, Status: ${status}, Message: ${message}`);
    ElMessage.warning(message || "Request failed");
};

function handleAxiosError(err, url, failure = defaultFailure) {
    const status = err?.response?.status;
    const message =
        err?.response?.data?.message ||
        err?.message ||
        "An error occurred. Please contact the administrator.";

    if (status === 401) {
        deleteAccessToken();
        const store = useStore();
        store.clear?.();
        ElMessage.warning("Login expired. Please sign in again.");
        router.replace("/login");
        return;
    }
    failure(message, status, url);
}

// ====== 基础请求封装 ======
function internalPost(url, data, headers, success, failure = defaultFailure) {
    axios
        .post(url, data, { headers })
        .then(({ data }) => {
            if (data?.code === 200) success(data.data);
            else failure(data?.message, data?.code, url);
        })
        .catch((err) => handleAxiosError(err, url, failure));
}

function internalGet(url, headers, success, failure = defaultFailure) {
    axios
        .get(url, { headers })
        .then(({ data }) => {
            if (data?.code === 200) success(data.data);
            else failure(data?.message, data?.code, url);
        })
        .catch((err) => handleAxiosError(err, url, failure));
}

// ====== 对外导出 ======
function login(username, password, remember, success, failure = defaultFailure) {
    const form = new URLSearchParams();
    form.append("username", username);
    form.append("password", password);
    internalPost(
        "/api/auth/login",
        form,
        { "Content-Type": "application/x-www-form-urlencoded" },
        (data) => {
            storeAccessToken(remember, data.token, data.expire);
            const store = useStore();
            store.user.role = data.role;
            store.user.username = data.username;
            store.user.email = data.email;
            ElMessage.success(`Login successful. Welcome, ${data.username}, to our system.`);
            success(data);
        },
        failure
    );
}

function post(url, data, success, failure = defaultFailure) {
    internalPost(url, data, accessHeader(), success, failure);
}

function get(url, success, failure = defaultFailure) {
    internalGet(url, accessHeader(), success, failure);
}

function logout(success, failure = defaultFailure) {
    get(
        "/api/auth/logout",
        () => {
            deleteAccessToken();
            const store = useStore();
            store.clear?.();
            ElMessage.success(`Logged out successfully. We hope to see you again.`);
            success();
        },
        failure
    );
}

function unauthorized() {
    return !takeAccessToken();
}

export { post, get, login, logout, unauthorized };
