<?php
/**
 * Noir+ · sincronización entre tele, móvil y Windows.
 *
 * Guarda, por cuenta, lo visto a medias, Mi lista y las valoraciones en un archivo JSON pequeño.
 * - La cuenta se identifica con una huella de tu servidor + usuario de IPTV (nunca se envía la contraseña).
 * - La primera vez que una cuenta guarda datos, queda ligada a su clave secreta; después solo esa clave puede leer o escribir.
 * - Si dos aparatos cambian lo mismo, gana el cambio más reciente.
 * - Cada aparato se apunta en "devices" (nombre y última vez). Un aparato excluido no puede escribir.
 *
 * Instalación: sube esta carpeta (sync.php + .htaccess) a tu hosting, por ejemplo a /public_html/noir/
 * La carpeta "data" se crea sola y queda protegida.
 */

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

const APP_KEY  = 'noirplus-sync-v1';       // la app envía esta cabecera; frena peticiones casuales
const MAX_SIZE = 512 * 1024;                // tamaño máximo por cuenta (512 KB)
const SECTIONS = ['progress', 'favs', 'ratings', 'settings', 'devices'];

function fail(int $code, string $msg) { http_response_code($code); echo json_encode(['error' => $msg]); exit; }

if (($_SERVER['HTTP_X_NOIR_APP'] ?? '') !== APP_KEY) fail(403, 'app');

$id    = $_SERVER['HTTP_X_NOIR_ID'] ?? '';
$token = $_SERVER['HTTP_X_NOIR_TOKEN'] ?? '';
if (!preg_match('/^[a-f0-9]{64}$/', $id) || !preg_match('/^[a-f0-9]{64}$/', $token)) fail(400, 'id');
$device     = $_SERVER['HTTP_X_NOIR_DEVICE'] ?? '';
$deviceName = mb_substr(trim(rawurldecode($_SERVER['HTTP_X_NOIR_DEVICE_NAME'] ?? '')), 0, 60);
if ($device !== '' && !preg_match('/^[a-zA-Z0-9-]{8,64}$/', $device)) fail(400, 'device');

$dir = __DIR__ . '/data';
if (!is_dir($dir)) {
    mkdir($dir, 0700, true);
    file_put_contents($dir . '/.htaccess', "Require all denied\nDeny from all\n");
    file_put_contents($dir . '/index.html', '');
}
$file = "$dir/$id.json";

$fp = fopen($file, 'c+');
if (!$fp) fail(500, 'io');
flock($fp, LOCK_EX);
$raw  = stream_get_contents($fp);
$data = $raw ? json_decode($raw, true) : null;
if (!is_array($data)) $data = ['owner' => null, 'updated' => 0];

// La cuenta queda ligada a su clave secreta la primera vez
$tokenHash = hash('sha256', 'noir|' . $token);
if ($data['owner'] === null) {
    $data['owner'] = $tokenHash;
} elseif (!hash_equals($data['owner'], $tokenHash)) {
    flock($fp, LOCK_UN); fclose($fp);
    fail(401, 'token');
}

// Aparato: se apunta (nombre, tipo y última vez). Si está excluido, no puede escribir.
if (!isset($data['devices']) || !is_array($data['devices'])) $data['devices'] = [];
$blocked = false;
if ($device !== '') {
    $d = $data['devices'][$device] ?? ['t' => 0, 'blocked' => false];
    $blocked = !empty($d['blocked']);
    $d['name'] = $deviceName !== '' ? $deviceName : ($d['name'] ?? 'Aparato');
    $d['kind'] = mb_substr($_SERVER['HTTP_X_NOIR_DEVICE_KIND'] ?? ($d['kind'] ?? ''), 0, 20);
    $d['seen'] = time();
    $data['devices'][$device] = $d;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && $blocked) {
    // Excluido: solo se le permite cambiar la propia lista de aparatos (por si se vuelve a incluir desde otro)
    $in = json_decode(file_get_contents('php://input'), true);
    if (is_array($in) && isset($in['devices']) && is_array($in['devices'])) {
        foreach ($in['devices'] as $key => $val) {
            if ($key === $device || !is_array($val) || !isset($val['t'])) continue;
            $old = $data['devices'][$key] ?? null;
            if ($old === null || (int)$val['t'] >= (int)($old['t'] ?? 0)) $data['devices'][$key] = array_merge($old ?? [], $val);
        }
    }
} elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $body = file_get_contents('php://input');
    if (strlen($body) > MAX_SIZE) { flock($fp, LOCK_UN); fclose($fp); fail(413, 'size'); }
    $in = json_decode($body, true);
    if (!is_array($in)) { flock($fp, LOCK_UN); fclose($fp); fail(400, 'json'); }

    // Mezcla por elemento: gana el que tenga la marca de tiempo "t" más reciente
    foreach (SECTIONS as $sec) {
        if (!isset($in[$sec]) || !is_array($in[$sec])) continue;
        if (!isset($data[$sec]) || !is_array($data[$sec])) $data[$sec] = [];
        foreach ($in[$sec] as $key => $val) {
            if (!is_array($val) || !isset($val['t'])) continue;
            $old = $data[$sec][$key] ?? null;
            if ($old === null || (int)$val['t'] >= (int)($old['t'] ?? 0))
                $data[$sec][$key] = $sec === 'devices' ? array_merge($old ?? [], $val) : $val;
        }
    }
    $data['updated'] = time();
}

{
    $out = json_encode($data, JSON_UNESCAPED_UNICODE);
    if (strlen($out) > MAX_SIZE * 2) { flock($fp, LOCK_UN); fclose($fp); fail(413, 'size'); }
    ftruncate($fp, 0); rewind($fp); fwrite($fp, $out); fflush($fp);
}

flock($fp, LOCK_UN);
fclose($fp);

$resp = $data;
unset($resp['owner']);
$resp['blocked'] = $blocked;
echo json_encode($resp, JSON_UNESCAPED_UNICODE);
