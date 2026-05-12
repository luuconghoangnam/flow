const HOST_NAME = "com.flow.download_manager";
const recentRequestHeaders = new Map();

chrome.webRequest.onBeforeSendHeaders.addListener(
    (details) => {
        const headers = {};
        let referrer;
        let cookies;
        let userAgent;

        for (const header of details.requestHeaders || []) {
            const name = header.name.toLowerCase();
            if (name === "host") {
                continue;
            }
            if (name === "referer") {
                referrer = header.value;
            } else if (name === "cookie") {
                cookies = header.value;
            } else if (name === "user-agent") {
                userAgent = header.value;
            } else if (header.value) {
                headers[header.name] = header.value;
            }
        }

        recentRequestHeaders.set(details.url, { headers, referrer, cookies, userAgent, at: Date.now() });
        for (const [url, value] of recentRequestHeaders) {
            if (Date.now() - value.at > 60_000) {
                recentRequestHeaders.delete(url);
            }
        }
    },
    { urls: ["<all_urls>"] },
    ["requestHeaders", "extraHeaders"]
);

function sendCommand(message_type, payload = {}) {
    return new Promise((resolve, reject) => {
        const port = chrome.runtime.connectNative(HOST_NAME);

        port.onMessage.addListener((response) => {
            if (response.status_code >= 200 && response.status_code < 300) {
                resolve(response);
            } else {
                reject(new Error(response.message || "Native host error"));
            }
        });

        port.onDisconnect.addListener(() => {
            if (chrome.runtime.lastError) {
                reject(new Error(chrome.runtime.lastError.message));
            }
        });

        port.postMessage({
            version: 1,
            message_type,
            payload,
        });
    });
}

chrome.downloads.onCreated.addListener((downloadItem) => {
    console.log("Intercepted download:", downloadItem);
    if (!/^https?:\/\//i.test(downloadItem.url || "")) {
        console.log("Flow ignored non-http download:", downloadItem.url);
        return;
    }
    const captured = recentRequestHeaders.get(downloadItem.url) || {};

    sendCommand("download.create", {
        url: downloadItem.url,
        file_name: downloadItem.filename ? downloadItem.filename.split(/[\\/]/).pop() : undefined,
        output_dir: undefined,
        connections: 8,
        headers: captured.headers,
        referrer: captured.referrer || downloadItem.referrer,
        cookies: captured.cookies,
        user_agent: captured.userAgent,
        expected_sha256_hex: undefined,
    })
        .then((response) => {
            console.log("Flow accepted download:", response);
            if (response.download_id) {
                chrome.downloads.cancel(downloadItem.id);
            }
        })
        .catch((error) => {
            console.error("Flow native host failed:", error);
        });
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (message?.type === "flow.download.status") {
        sendCommand("download.status", { download_id: message.download_id })
            .then(sendResponse)
            .catch((error) => sendResponse({ status_code: 500, message: error.message }));
        return true;
    }

    if (message?.type === "flow.download.list") {
        sendCommand("download.list", {})
            .then(sendResponse)
            .catch((error) => sendResponse({ status_code: 500, message: error.message }));
        return true;
    }

    if (message?.type === "flow.download.retry") {
        sendCommand("download.retry", { download_id: message.download_id })
            .then(sendResponse)
            .catch((error) => sendResponse({ status_code: 500, message: error.message }));
        return true;
    }

    if (message?.type === "flow.download.cleanup") {
        sendCommand("download.cleanup", { statuses: message.statuses })
            .then(sendResponse)
            .catch((error) => sendResponse({ status_code: 500, message: error.message }));
        return true;
    }

    if (message?.type === "flow.download.start") {
        sendCommand("download.start", { download_id: message.download_id })
            .then(sendResponse)
            .catch((error) => sendResponse({ status_code: 500, message: error.message }));
        return true;
    }

    if (message?.type === "flow.download.stop") {
        sendCommand("download.stop", { download_id: message.download_id })
            .then(sendResponse)
            .catch((error) => sendResponse({ status_code: 500, message: error.message }));
        return true;
    }

    if (message?.type === "flow.queue.list") {
        sendCommand("queue.list", {})
            .then(sendResponse)
            .catch((error) => sendResponse({ status_code: 500, message: error.message }));
        return true;
    }

    if (message?.type === "flow.queue.create") {
        sendCommand("queue.create", message.payload || {})
            .then(sendResponse)
            .catch((error) => sendResponse({ status_code: 500, message: error.message }));
        return true;
    }

    if (message?.type === "flow.queue.update") {
        sendCommand("queue.update", message.payload || {})
            .then(sendResponse)
            .catch((error) => sendResponse({ status_code: 500, message: error.message }));
        return true;
    }

    if (message?.type === "flow.queue.delete") {
        sendCommand("queue.delete", message.payload || {})
            .then(sendResponse)
            .catch((error) => sendResponse({ status_code: 500, message: error.message }));
        return true;
    }

    if (message?.type === "flow.queue.move") {
        sendCommand("queue.move", message.payload || {})
            .then(sendResponse)
            .catch((error) => sendResponse({ status_code: 500, message: error.message }));
        return true;
    }

    if (message?.type === "flow.queue.requeue") {
        sendCommand("queue.requeue", message.payload || {})
            .then(sendResponse)
            .catch((error) => sendResponse({ status_code: 500, message: error.message }));
        return true;
    }

    if (message?.type === "flow.queue.events") {
        sendCommand("queue.events", message.payload || { limit: 50 })
            .then(sendResponse)
            .catch((error) => sendResponse({ status_code: 500, message: error.message }));
        return true;
    }

    return false;
});
