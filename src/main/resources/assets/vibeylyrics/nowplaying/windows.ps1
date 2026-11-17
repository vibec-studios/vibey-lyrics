# Vibey Lyrics: reports what plays on this PC (Spotify, browser, media player ...) through the Windows media
# controls (the same info as the volume flyout). One JSON line every ~0.4 s.
# Args: <pid of the game> <command file>. Writing "toggle" into the command file toggles play/pause; the script ends
# by itself when the game is gone.
param([int]$GamePid = 0, [string]$CommandFile = '')
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
} catch {
    [Console]::Out.WriteLine('{"error":"unsupported"}')
    exit 1
}
$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1

function Await($op, [Type]$type) {
    $task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
    if (-not $task.Wait(3000)) { return $null }
    return $task.Result
}

$mgrType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$mgr = Await ($mgrType::RequestAsync()) $mgrType
if ($null -eq $mgr) {
    [Console]::Out.WriteLine('{"error":"unsupported"}')
    exit 1
}
$ErrorActionPreference = 'Continue'

function Pick() {
    foreach ($x in $mgr.GetSessions()) {
        if ($x.GetPlaybackInfo().PlaybackStatus -eq 'Playing') { return $x }
    }
    return $mgr.GetCurrentSession()
}

while ($true) {
    if ($GamePid -gt 0 -and -not (Get-Process -Id $GamePid -ErrorAction SilentlyContinue)) { exit 0 }
    if ($CommandFile -and (Test-Path -LiteralPath $CommandFile)) {
        $cmd = (Get-Content -LiteralPath $CommandFile -Raw).Trim()
        Remove-Item -LiteralPath $CommandFile -ErrorAction SilentlyContinue
        if ($cmd -eq 'quit') { exit 0 }
        if ($cmd -eq 'toggle') {
            $s = Pick
            if ($s) { $null = Await ($s.TryTogglePlayPauseAsync()) ([bool]) }
        }
    }
    try {
        $session = Pick
        if (-not $session) {
            [Console]::Out.WriteLine('{"active":false}')
        } else {
            $info = $session.GetPlaybackInfo()
            $tl = $session.GetTimelineProperties()
            $props = Await ($session.TryGetMediaPropertiesAsync()) $propsType
            $playing = ($info.PlaybackStatus -eq 'Playing')
            $pos = $tl.Position.TotalMilliseconds
            # The player reports its position now and then; move it on by the time since that report.
            if ($playing -and $tl.LastUpdatedTime.Year -gt 2000) {
                $pos += ([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalMilliseconds
            }
            $dur = ($tl.EndTime - $tl.StartTime).TotalMilliseconds
            if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }
            $o = [ordered]@{
                active  = $true
                app     = [string]$session.SourceAppUserModelId
                title   = [string]$props.Title
                artist  = [string]$props.Artist
                album   = [string]$props.AlbumTitle
                playing = $playing
                pos     = [long]$pos
                dur     = [long]$dur
            }
            [Console]::Out.WriteLine(($o | ConvertTo-Json -Compress))
        }
    } catch {
        [Console]::Out.WriteLine('{"active":false}')
    }
    [Console]::Out.Flush()
    Start-Sleep -Milliseconds 400
}
