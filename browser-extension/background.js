// background.js
chrome.downloads.onCreated.addListener((downloadItem) => {
    console.log("Intercepted download:", downloadItem);
    
    // Connect to the native messaging host
    const port = chrome.runtime.connectNative("com.rust.abdm.host");
    
    // Send the URL to the Rust app
    port.postMessage({
        action: "download",
        url: downloadItem.url,
        filename: downloadItem.filename
    });
    
    // Listen for response from Rust
    port.onMessage.addListener((response) => {
        console.log("Received response from Rust App:", response);
        if (response.status === "success") {
            // Cancel the browser's default download since the Rust app handles it
            chrome.downloads.cancel(downloadItem.id);
        }
    });
});
