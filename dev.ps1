# Pass the absolute path of the built web assets to Gradle
$webRoot = Join-Path (Get-Location) "src/main/resources/web/dist"
# Pass property BEFORE task name to ensure it's treated as a JVM/Gradle option
$gradleProcess = Start-Process -FilePath "./gradlew" -ArgumentList "-Dmclivetracker.dev.web-root=$webRoot", "runClient" -PassThru -NoNewWindow

# Using cmd /c or pnpm.cmd is required for Start-Process on Windows if pnpm is a batch file
# Use "watch" to rebuild on change (write to disk) so the mod server can serve it
Start-Process -FilePath "cmd" -ArgumentList "/c pnpm run watch" -WorkingDirectory "src/main/resources/web" -NoNewWindow

# Wait for Gradle process to exit (Minecraft closed)
# Note: This simple script doesn't automatically kill the Vite server when Minecraft closes, 
# but it runs them in the same window (or separate if configured). 
# For true parallel execution in one terminal, we just started them. 
# The user can Ctrl+C to stop both if running in foreground.

# Actually, to make it cleaner, let's keep the script running.
Wait-Process -Id $gradleProcess.Id
