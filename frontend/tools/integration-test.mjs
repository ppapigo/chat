import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const windows = process.platform === 'win32';
const child = spawn(windows ? 'powershell.exe' : './gradlew', windows
  ? ['-NoProfile', '-NonInteractive', '-Command', '& .\\gradlew.bat --no-daemon -g .cache/gradle browserIntegrationTest']
  : ['--no-daemon', '-g', '.cache/gradle', 'browserIntegrationTest'], { cwd: fileURLToPath(new URL('../../', import.meta.url)), stdio: 'inherit', windowsHide: true });
child.on('error', error => { console.error(error.message); process.exitCode = 1; });
child.on('exit', code => { process.exitCode = code ?? 1; });
