# EC2 테스트 배포

Ubuntu 24.04, 메모리 4GB, 디스크 30GB를 기준으로 합니다. 보안 그룹에는 SSH(내 IP), HTTP(80), HTTPS(443)만 엽니다. 이 절차는 토스 테스트 키와 테스트 데이터용입니다.

1. EC2의 퍼블릭 IPv4 주소와 내려받은 키 페어 파일을 확인합니다. Windows PowerShell에서 접속합니다.

   ```powershell
   ssh -i "C:\path\to\key.pem" ubuntu@<퍼블릭-IP>
   ```

2. 서버에 [Docker Engine과 Compose 플러그인](https://docs.docker.com/engine/install/ubuntu/)을 설치한 뒤 저장소를 받습니다.

   ```sh
   git clone https://github.com/JeongHeum2802/Free_Ticket.git
   cd Free_Ticket/code/backend
   cp .env.deploy.example .env.deploy
   nano .env.deploy
   ```

3. `.env.deploy`에서 `PUBLIC_HOST`를 EC2 주소로 바꿉니다. 예를 들어 IP가 `203.0.113.10`이면 `203-0-113-10.sslip.io`입니다. DB 비밀번호 두 개는 서로 다르게 설정하고, `JWT_SECRET`에는 `openssl rand -hex 32`로 만든 값을 넣습니다. 토스 테스트 키와 네이버 지도 클라이언트 ID도 입력합니다. 비밀 키는 Git에 올리지 않습니다.

4. 서비스를 시작합니다.

   ```sh
   chmod 600 .env.deploy
   sudo docker compose --env-file .env.deploy -f docker-compose.deploy.yml up -d --build
   sudo docker compose --env-file .env.deploy -f docker-compose.deploy.yml ps
   ```

5. `https://<PUBLIC_HOST 값>`에서 화면을 열고 회원가입, 로그인, 공연 목록, 테스트 결제를 확인합니다. 지도용 웹 서비스 URL에도 이 HTTPS 주소를 등록합니다. 새 DB에는 `backup.sql`의 테이블만 생성되며 공연 데이터는 없습니다.

EC2를 중지했다가 시작하면 자동 할당 퍼블릭 IP가 바뀝니다. 그때는 `.env.deploy`의 `PUBLIC_HOST`를 새 IP 주소로 바꾸고 4번 명령을 다시 실행합니다. 고정 주소가 필요하면 Elastic IP를 연결할 수 있지만 중지 중에도 IP 요금이 듭니다. EC2를 종료하기 전에는 DB 데이터를 백업합니다.
