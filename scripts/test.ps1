$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $projectRoot 'build\state-tests'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$javaDirectory = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac).Source }
& (Join-Path $javaDirectory 'javac.exe') -d $outputDirectory (Join-Path $projectRoot 'app\src\main\java\com\arao\pomodoro\TimerState.java') (Join-Path $projectRoot 'app\src\main\java\com\arao\pomodoro\JiraKey.java') (Join-Path $projectRoot 'app\src\test\java\com\arao\pomodoro\TimerStateTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Timer test compilation failed.' }
& (Join-Path $javaDirectory 'java.exe') -cp $outputDirectory com.arao.pomodoro.TimerStateTest
if ($LASTEXITCODE -ne 0) { throw 'Timer tests failed.' }
