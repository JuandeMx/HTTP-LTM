# Script to download and set up Go portable and gomobile
$ErrorActionPreference = "Stop"

$workspaceDir = "d:\apk"
$goZipPath = "$workspaceDir\go.zip"
$goDestDir = "$workspaceDir\go"

# 1. Download Go portable if not already downloaded/extracted
if (-not (Test-Path "$goDestDir\bin\go.exe")) {
    Write-Output "Go not found. Downloading Go 1.22.4 (Windows AMD64)..."
    $url = "https://go.dev/dl/go1.22.4.windows-amd64.zip"
    
    # Use Invoke-WebRequest to download the zip file
    Invoke-WebRequest -Uri $url -OutFile $goZipPath -TimeoutSec 300
    Write-Output "Successfully downloaded Go."
    
    Write-Output "Extracting Go..."
    Expand-Archive -Path $goZipPath -DestinationPath $workspaceDir -Force
    Write-Output "Go extracted to $goDestDir."
    
    # Clean up zip file
    if (Test-Path $goZipPath) {
        Remove-Item $goZipPath -Force
    }
} else {
    Write-Output "Go is already installed at $goDestDir."
}

# 2. Setup Environment Variables
$env:GOROOT = $goDestDir
$env:GOPATH = "$workspaceDir\gopath"
$env:PATH = "$goDestDir\bin;$env:GOPATH\bin;" + $env:PATH
$env:ANDROID_NDK_HOME = "C:\Users\JGJua\AppData\Local\Android\Sdk\ndk\25.1.8937393"

Write-Output "=== Go version ==="
& go version

Write-Output "=== Installing gomobile ==="
$env:GOPROXY = "https://proxy.golang.org,direct"
& go install golang.org/x/mobile/cmd/gomobile@latest
& go install golang.org/x/mobile/cmd/gobind@latest

Write-Output "=== Initializing gomobile ==="
& gomobile init

Write-Output "Environment setup completed successfully!"
