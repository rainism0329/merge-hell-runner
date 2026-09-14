[CmdletBinding()]
param(
    [ValidatePattern('^:?[A-Za-z0-9:_-]+$')]
    [string[]]$Tasks = @('test', 'build', 'buildPlugin', 'verifyPlugin'),
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Offline,
    [switch]$Clean,
    [switch]$NoProxy
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    throw 'Set JAVA_HOME to a JDK 17 installation, or pass -JavaHome.'
}
$jdkRoot = (Resolve-Path -LiteralPath $JavaHome).Path
$javaExecutable = Join-Path $jdkRoot 'bin/java.exe'
$javacExecutable = Join-Path $jdkRoot 'bin/javac.exe'
if (!(Test-Path -LiteralPath $javaExecutable) -or !(Test-Path -LiteralPath $javacExecutable)) {
    throw 'The selected JavaHome must contain both java.exe and javac.exe from JDK 17.'
}

# Read the compiler version without changing the user's Java or IDE configuration.
$compilerOutput = & $javacExecutable -version 2>&1 | Out-String
$compilerVersion = [regex]::Match($compilerOutput, '(?m)^javac 17(?:\.\S+)?\s*$').Value.Trim()
if ($LASTEXITCODE -ne 0 -or !$compilerVersion) {
    throw 'This verification entry point requires JDK 17, matching the plugin release baseline.'
}

$previousJavaHome = $env:JAVA_HOME
$previousGradleUserHome = $env:GRADLE_USER_HOME
$previousJavaOptions = $env:JAVA_OPTS
$previousCi = $env:CI
$cacheRoot = Join-Path $projectRoot '.gradle/evolution-user-home'
$logRoot = Join-Path $projectRoot '.gradle/verification-logs'
New-Item -ItemType Directory -Path $cacheRoot, $logRoot -Force | Out-Null
$logPath = Join-Path $logRoot ((Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.log')
$proxyArguments = [Collections.Generic.List[string]]::new()

if (!$NoProxy) {
    foreach ($protocol in @('http', 'https')) {
        $proxyValue = [Environment]::GetEnvironmentVariable($protocol.ToUpperInvariant() + '_PROXY')
        if ([string]::IsNullOrWhiteSpace($proxyValue)) { continue }
        $proxyAddress = $null
        if (![Uri]::TryCreate($proxyValue, [UriKind]::Absolute, [ref]$proxyAddress) -or
            $proxyAddress.Scheme -ne 'http' -or !$proxyAddress.Host) {
            throw "The $($protocol.ToUpperInvariant())_PROXY value must be an HTTP proxy URL. Use -NoProxy to bypass environment proxy settings."
        }
        if ($proxyAddress.UserInfo) {
            throw 'Authenticated proxy URLs are not copied to JVM command lines. Configure JVM proxy authentication separately and use -NoProxy.'
        }
        $proxyArguments.Add("-D$protocol.proxyHost=$($proxyAddress.Host)")
        $proxyArguments.Add("-D$protocol.proxyPort=$($proxyAddress.Port)")
    }
}

$gradleArguments = [Collections.Generic.List[string]]::new()
if ($Clean) { $gradleArguments.Add('clean') }
foreach ($taskName in $Tasks) { $gradleArguments.Add($taskName) }
$gradleArguments.Add('--no-daemon')
$gradleArguments.Add('--console=plain')
$gradleArguments.Add('-Dorg.gradle.internal.http.connectionTimeout=20000')
$gradleArguments.Add('-Dorg.gradle.internal.http.socketTimeout=30000')
if ($Offline) { $gradleArguments.Add('--offline') }
if ($VerbosePreference -eq 'Continue') { $gradleArguments.Add('--info') }
foreach ($proxyArgument in $proxyArguments) { $gradleArguments.Add($proxyArgument) }

Push-Location -LiteralPath $projectRoot
try {
    $env:JAVA_HOME = $jdkRoot
    $env:GRADLE_USER_HOME = $cacheRoot
    # The IntelliJ plugin omits optional IDE source archives in CI; verification uses binaries.
    $env:CI = 'true'
    # JAVA_OPTS applies during wrapper bootstrap; Gradle arguments also reach its daemon.
    $env:JAVA_OPTS = (@($previousJavaOptions, ($proxyArguments -join ' ')) |
        Where-Object { ![string]::IsNullOrWhiteSpace($_) }) -join ' '
    Write-Host "JDK: $compilerVersion"
    Write-Host "Gradle cache: $cacheRoot"
    Write-Host "Verification log: $logPath"
    & (Join-Path $projectRoot 'gradlew.bat') @gradleArguments 2>&1 |
        Tee-Object -FilePath $logPath
    $gradleExitCode = $LASTEXITCODE
    if ($gradleExitCode -ne 0) {
        throw "Gradle exited with code $gradleExitCode. See $logPath"
    }
    Write-Host "Verification passed. Log: $logPath"
}
finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:GRADLE_USER_HOME = $previousGradleUserHome
    $env:JAVA_OPTS = $previousJavaOptions
    $env:CI = $previousCi
    Pop-Location
}
