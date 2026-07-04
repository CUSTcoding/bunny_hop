# Bunny Hop — Alertas de Cheias via BLE

Sistema MVP para avisar cidadãos quando entram no alcance de uma **zona afetada por cheias**. Cada zona tem um beacon BLE (PC Linux + BlueZ) que anuncia o alerta; a app Android escaneia, mostra **notificação + diálogo** e pode ainda **retransmitir o alerta para outros telemóveis próximos** via BLE.

O objetivo atual é testar um protótipo simples e funcional de propagação local, sem depender de internet.

## Zonas configuradas

| ID | Nome BLE | Descrição |
|----|----------|-----------|
| `1` | `CHEIAS_ZONA_1` | Zona 1 — Baixo Mondego |
| `2` | `CHEIAS_ZONA_2` | Zona 2 — Ribeira de Coimbra |
| `3` | `CHEIAS_ZONA_3` | Zona 3 — Figueira da Foz |

Definidas em:
- `bunny_hop_beacon/bunny_hop_constants.py`
- `android/BunnyHop/app/src/main/java/com/bunnyhop/app/BunnyHopConfig.java`

**UUID partilhado (todas as zonas):** `7f3a9c21-b5e4-4d8f-a1c6-2e8b4f9d0a73`

---

## 1. Beacon no PC (Linux + BlueZ)

### Pré-requisitos

```bash
sudo apt update
sudo apt install -y bluez python3-gi python3-dbus libglib2.0-dev python3-venv

python3 -m venv .venv
source .venv/bin/activate
pip install -r bunny_hop_beacon/requirements.txt
```

- **BlueZ** ≥ 5.43 (API LE Advertising via D-Bus)
- Adaptador Bluetooth com suporte a **peripheral / LE advertising**
- Serviço activo: `sudo systemctl enable --now bluetooth`

### Permissões

```bash
# Opção A — sudo (teste rápido)
cd bunny_hop_beacon
sudo python3 bunny_hop_beacon.py --zona 1

# Opção B — capability (evita sudo depois)
sudo setcap cap_net_admin+eip "$(readlink -f "$(which python3)")"
python3 bunny_hop_beacon.py --zona 1
```

### Executar por zona

```bash
cd bunny_hop_beacon
python3 bunny_hop_beacon.py --list-zonas   # ver zonas disponíveis
python3 bunny_hop_beacon.py --zona 1       # alerta Baixo Mondego
python3 bunny_hop_beacon.py --zona 2       # alerta Ribeira de Coimbra
```

Saída esperada:

```
[Bunny Hop] Adaptador: XX:XX:XX:XX:XX:XX
[Bunny Hop] Zona: Zona 1 — Baixo Mondego
[Bunny Hop] Nome BLE: CHEIAS_ZONA_1
[Bunny Hop] Service UUID: 7f3a9c21-b5e4-4d8f-a1c6-2e8b4f9d0a73
[Bunny Hop] Alerta activo — a anunciar cheias em: Zona 1 — Baixo Mondego
```

Para cobrir **várias zonas em simultâneo**, use um PC/dongle BLE por zona, cada um com `--zona` diferente.

### Confirmar advertising

```bash
bluetoothctl
power on
scan on
# Procure CHEIAS_ZONA_1, CHEIAS_ZONA_2, etc.
```

Ou use **nRF Connect** no telemóvel — deve ver o nome da zona + UUID na lista de Service UUIDs.

---

## 2. App Android (Bunny Hop)

### Abrir no Android Studio

1. **File → Open** → pasta `android/BunnyHop`
2. Sincronize Gradle e ligue um telemóvel físico (emulador **não** recebe BLE real)
3. Run ▶

### Permissões (Android 12+)

- `BLUETOOTH_SCAN` com `neverForLocation`
- `BLUETOOTH_CONNECT`
- `POST_NOTIFICATIONS` (Android 13+)

### Uso

1. Inicie o beacon da zona no PC: `sudo python3 bunny_hop_beacon.py --zona 1`
2. No telemóvel: abra **Bunny Hop** → **Iniciar alerta**
3. Aproxime-se do beacon (1–5 m)
4. Na **primeira detecção**: diálogo + notificação *"Alerta de cheias em Zona 1 — Baixo Mondego."*
5. Se quiser testar a repetição local, toque em **Repetir alerta agora** para o telemóvel anunciar o alerta para outros aparelhos próximos.
6. Várias zonas podem estar activas ao mesmo tempo — cada uma notifica uma vez ao entrar em alcance.
7. **Parar alerta** para terminar o scan.

### Admin local de alertas

- A app inclui um ecrã de administração (`Admin Alertas`) para registar alertas no SQLite local.
- Cada alerta armazena `device_name`, `message`, `active` e `channels`.
- Os canais disponíveis são `BLE`, `NOTIFICATION`, `SMS`, `USSD` e `HTTP`.
- Para o MVP atual, o foco está em **BLE + notificação + SMS**, com a retransmissão local para outros telemóveis.

### Botões da app

- **Iniciar alerta**: inicia o scan BLE.
- **Repetir alerta agora**: faz o telemóvel retransmitir o alerta BLE para aparelhos próximos.
- **Testar alerta SMS**: dispara um alerta de teste por SMS (ajusta o número no código/configuração, se necessário).

### Debounce (por zona)

- Notifica **uma vez** ao entrar em alcance de cada zona
- Se o sinal desaparecer por **10 s**, a zona passa a “fora de alcance”
- Só notifica de novo após perder e **reencontrar** o beacon dessa zona

---

## 3. Teste end-to-end

| # | Acção | Resultado esperado |
|---|--------|-------------------|
| 1 | `sudo python3 bunny_hop_beacon.py --zona 1` | Log “Alerta activo” |
| 2 | nRF Connect, scan | Vê `CHEIAS_ZONA_1` + UUID |
| 3 | Abrir Bunny Hop, conceder permissões | Ecrã idle |
| 4 | **Iniciar alerta** | “Scan ativo…” |
| 5 | Aproximar telemóvel do beacon | Diálogo + notificação de cheias |
| 6 | Tocar em **Repetir alerta agora** | O telemóvel começa a anunciar o alerta BLE para aparelhos próximos |
| 7 | Num segundo telemóvel, abrir a app e ficar perto | O segundo telemóvel deteta o anúncio e mostra o alerta |
| 8 | Afastar >10 s, aproximar de novo | Nova notificação |
| 9 | **Parar alerta** / Ctrl+C no PC | Scan/advertising param |

### Troubleshooting

| Problema | Causa provável | Solução |
|----------|----------------|---------|
| `register_advertisement` falha | Sem permissão / adaptador sem peripheral | `sudo` ou `setcap`; dongle USB BLE |
| Android não vê beacon | BT desligado ou filtro UUID | Confirmar UUID no nRF Connect |
| Notificação não aparece | POST_NOTIFICATIONS negada | Configurações → Apps → Bunny Hop |
| Retransmissão BLE não acontece | Permissões BLE/advertiser ausentes ou dispositivo incompatível | Confirmar permissões `BLUETOOTH_ADVERTISE` e usar um telemóvel com suporte BLE advertiser |
| `ModuleNotFoundError: bluezero` | Dependências Python em falta | `pip install -r bunny_hop_beacon/requirements.txt` |

---

## 4. Estrutura

```
bunny_hop/
├── README.md
├── bunny_hop_beacon/
│   ├── bunny_hop_beacon.py      # Beacon por zona (--zona N)
│   ├── bunny_hop_constants.py   # Zonas + UUID
│   └── requirements.txt
└── android/BunnyHop/
    └── app/src/main/java/com/bunnyhop/app/
        ├── MainActivity.java    # Scan multi-zona + alertas
        └── BunnyHopConfig.java  # Mapa de zonas (sync com Python)
```

---

## 5. Próximos passos

- **Foreground Service** no Android para scan contínuo em background
- **WebSocket / HTTP** para activar zonas remotamente a partir de um centro de operações
- **Manufacturer data** no pacote BLE para nível de risco (amarelo/vermelho)
- Adicionar zonas em `bunny_hop_constants.py` e `BunnyHopConfig.java`
