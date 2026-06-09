# Oracle Cloud Always Free VM 배포 가이드

대상: Spring Boot 단일 JAR + Oracle Autonomous DB(Wallet) 구성.
방식: AMD Micro(1GB)+스왑 VM + systemd + Nginx(IP 운영, HTTPS 추후).
(ARM A1 6GB 가 용량 풀리면 그쪽이 더 좋음 — 그땐 -Xmx 만 올리면 됨)

---

## 0. 준비물 체크
- OCI 계정 (Always Free)
- Oracle Autonomous DB **Wallet** (`src/main/resources/Wallet_IntranetDB`) ← 이미 보유
- 로컬에 JDK 21, Node, Gradle (gradlew)

> **DB(ADB) 관련**: 로컬에서 앱이 DB에 붙어 동작했다면 **이미 Autonomous DB가 있는 것**이다.
> 그 경우 새로 만들 필요 없이 기존 Wallet/계정을 VM에서 그대로 쓰면 된다.
> (OCI 콘솔 → Autonomous Database 에서 해당 DB가 "Always Free" 인지만 확인.)
> 정말 없다면 → 콘솔에서 "Create Autonomous Database" → Always Free 선택 →
> 생성 후 **Database Connection → Download Wallet** 로 Wallet zip 을 받아 사용.

---

## 1. 로컬: 배포 JAR 빌드
```powershell
npm install
npm run build          # webpack 정적 번들
npm run css:build      # tailwind output.css
.\gradlew.bat clean bootJar
```
결과물: `build\libs\bootcamp-0.0.1-SNAPSHOT.jar`

> 코드는 이미 환경변수 대응 완료(`application.properties`):
> `TNS_ADMIN_DIR`, `DB_PASSWORD`, `GEMINI_API_KEY`, `HOLIDAY_API_KEY`, `SERVER_PORT`
> 를 환경변수로 덮어쓸 수 있고, 없으면 로컬 기본값으로 동작한다.

---

## 2. OCI 콘솔: 인프라 생성

### 2-1. Compute 인스턴스 생성
1. 햄버거 메뉴(≡) → **Compute → Instances → Create instance**
   - Shape: **VM.Standard.E2.1.Micro (AMD, 1 OCPU/1GB, Always Free)**
     - (ARM `VM.Standard.A1.Flex` 6GB 가 더 좋지만 용량 부족 에러 나면 AMD 로)
   - Image: Oracle Linux 9
   - **★ Public IPv4 address 토글 ON** (네트워킹 단계 — 안 켜면 외부 접속 불가)
   - **SSH key**: "Generate a key pair" → **private key 다운로드** 후 안전한 곳에 보관 (1회만 표시)
2. 생성 후 **Public IP** 확인

### 2-2. 네트워킹 포트 열기 (Security List Ingress)
외부 접속 포트는 **서브넷의 Security List Ingress 규칙**으로 연다.

1. **Compute → Instances → (인스턴스 클릭)** → 상세 화면의 **Subnet** 링크 클릭
   - (또는 Networking → Virtual Cloud Networks → VCN → Subnets → 서브넷)
2. 서브넷 화면 → **Security Lists** → "Default Security List for ..." 클릭
3. **Security Rules → Ingress Rules → Add Ingress Rules**
4. 아래 값으로 규칙 추가:

   | 필드 | 값 |
   |---|---|
   | Stateless | 체크 안 함 |
   | Source Type | CIDR |
   | Source CIDR | `0.0.0.0/0` |
   | IP Protocol | TCP |
   | Destination Port Range | `80,443` |

5. **22(SSH)** 는 기본 보안 목록에 이미 열려 있는 경우가 많음. 없으면 같은 방식으로 `22` 추가.

> ⚠️ 여기서 열어도 VM 내부 **OS 방화벽(firewalld)** 을 따로 안 열면 접속 안 됨 → 3단계 참고.
> 둘(OCI 보안목록 + OS 방화벽)이 **모두** 열려야 외부 접속 가능.

---

## 3. VM 접속 & 기본 세팅
로컬 PowerShell:
```powershell
ssh -i C:\path\to\private_key opc@<PUBLIC_IP>
```
VM 안에서:
```bash
# OS 방화벽 열기 (OCI Linux 는 기본 차단 — 가장 흔한 함정!)
sudo firewall-cmd --permanent --add-port=80/tcp --add-port=443/tcp
sudo firewall-cmd --reload

# JDK 21
sudo dnf install -y java-21-openjdk

# ★ 스왑 2GB (AMD 1GB VM 필수 — 없으면 메모리 부족으로 기동 실패)
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab   # 재부팅 후에도 유지
free -h   # Swap 2.0Gi 보이면 OK

# 앱 디렉터리
sudo mkdir -p /opt/team/wallet /opt/team/uploads
sudo chown -R opc:opc /opt/team
```

---

## 4. 배포물 업로드
로컬 PowerShell (`<키>`, `<IP>` 치환):
```powershell
# JAR
scp -i <키> build\libs\bootcamp-0.0.1-SNAPSHOT.jar opc@<IP>:/opt/team/

# Wallet (폴더 내용물 전부)
scp -i <키> src\main\resources\Wallet_IntranetDB\* opc@<IP>:/opt/team/wallet/

# systemd / env / nginx 설정 파일
scp -i <키> deploy\team.service     opc@<IP>:/tmp/
scp -i <키> deploy\team.env.example opc@<IP>:/tmp/
scp -i <키> deploy\nginx-team.conf  opc@<IP>:/tmp/
```

---

## 5. 환경변수 파일 + 서비스 등록
VM 안에서:
```bash
# 환경변수 파일 (최소 TNS_ADMIN_DIR 필수)
cp /tmp/team.env.example /opt/team/team.env
chmod 600 /opt/team/team.env
# (DB_PASSWORD 등을 JAR 기본값과 다르게 쓰고 싶으면 이 파일에서 채운다)

# systemd 서비스 등록
sudo cp /tmp/team.service /etc/systemd/system/team.service
sudo systemctl daemon-reload
sudo systemctl enable --now team

# 기동 로그 확인 (DB 연결/포트 바인딩 확인)
sudo journalctl -u team -f
```
정상 기동되면 임시로 `http://<IP>:8080` 동작 확인 (8080 을 잠깐 열거나 바로 6단계로).

> **첫 기동 실패 시 흔한 원인**
> - DB 연결 실패 → `team.env` 의 `TNS_ADMIN_DIR=/opt/team/wallet` 확인, wallet 파일 업로드 확인
> - 스키마 검증 오류 → 새 ADB 라면 테이블이 없을 수 있음. 로컬에서 쓰던 ADB 를 그대로 쓰면 OK.

---

## 6. Nginx 리버스 프록시 (IP 운영)
VM 안에서:
```bash
sudo dnf install -y nginx
sudo cp /tmp/nginx-team.conf /etc/nginx/conf.d/team.conf
# 기본 server 블록과 충돌 시 /etc/nginx/nginx.conf 의 기본 server{} 제거
sudo nginx -t && sudo systemctl enable --now nginx
```
→ 브라우저에서 **http://\<PUBLIC_IP\>** 접속 확인.

---

## 7. (나중에) 도메인 + HTTPS
도메인을 Public IP 에 연결한 뒤:
```bash
sudo dnf install -y certbot python3-certbot-nginx
sudo certbot --nginx        # 무료 Let's Encrypt 인증서 자동 설정
```

---

## 재배포 (코드 수정 후)
```powershell
.\gradlew.bat clean bootJar
scp -i <키> build\libs\bootcamp-0.0.1-SNAPSHOT.jar opc@<IP>:/opt/team/
```
```bash
sudo systemctl restart team
```

## 운영 팁
- 로그: `sudo journalctl -u team -f`
- 업로드 파일은 `/opt/team/uploads` 에 영속 (재배포해도 유지)
- 재부팅 시 자동 시작 (`enable` 됨)
- 메모리 부족(OOM)나면 `team.service` 의 `-Xmx` 낮추거나 스왑 추가
