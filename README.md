# Network Inspector Mobile

Aplicativo Android para diagnóstico básico de redes Wi-Fi domésticas.

## Recursos
- SSID, IP local, BSSID, intensidade RSSI, frequência e velocidade do link.
- Teste de ping para 1.1.1.1.
- Resolução DNS de example.com.
- Varredura simples de hosts na sub-rede local (somente hosts que respondem a ICMP).
- Teste aproximado de download usando Cloudflare.
- Relatório copiável.

## Limitações
- Android pode ocultar SSID/BSSID/frequência sem permissões de localização e serviços de localização ativos.
- A varredura não identifica todos os dispositivos: roteadores e aparelhos podem bloquear ICMP; não é um inventário confiável de clientes Wi-Fi.
- O teste de download depende de conexão externa e mede o caminho até a Cloudflare; não substitui o teste contratado pela operadora.
- O aplicativo não altera configurações do roteador nem mede upload nesta versão.

## Compilar
O GitHub Actions gera um APK de depuração em cada push para `main`. Acesse **Actions**, abra a execução mais recente e baixe o artefato `NetworkInspectorMobile-debug`.
