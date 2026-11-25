$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $projectRoot 'build\state-tests'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$javaDirectory = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac).Source }
$source = Join-Path $projectRoot 'app\src\main\java\com\arao\pomodoro'
$tests = Join-Path $projectRoot 'app\src\test\java\com\arao\pomodoro'
& (Join-Path $javaDirectory 'javac.exe') -d $outputDirectory (Join-Path $source 'TimerState.java') (Join-Path $source 'JiraKey.java') (Join-Path $source 'MeetingSchedule.java') (Join-Path $source 'MeetingPlanner.java') (Join-Path $tests 'TimerStateTest.java') (Join-Path $tests 'MeetingPlannerTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Timer test compilation failed.' }
& (Join-Path $javaDirectory 'java.exe') -cp $outputDirectory com.arao.pomodoro.TimerStateTest
if ($LASTEXITCODE -ne 0) { throw 'Timer tests failed.' }
& (Join-Path $javaDirectory 'java.exe') -cp $outputDirectory com.arao.pomodoro.MeetingPlannerTest
if ($LASTEXITCODE -ne 0) { throw 'Meeting tests failed.' }
