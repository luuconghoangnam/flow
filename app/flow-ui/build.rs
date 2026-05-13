fn main() {
    println!("cargo:rerun-if-changed=ui/main.slint");
    println!("cargo:rerun-if-changed=assets/flow.ico");
    slint_build::compile("ui/main.slint").expect("Failed to compile Slint UI");

    #[cfg(target_os = "windows")]
    {
        let mut resource = winresource::WindowsResource::new();
        resource.set_icon("assets/flow.ico");
        resource.set("ProductName", "Flow");
        resource.set("FileDescription", "Flow");
        resource.set("OriginalFilename", "flow.exe");
        resource.set("InternalName", "Flow");
        resource.compile().expect("Failed to compile Windows resources");
    }
}
