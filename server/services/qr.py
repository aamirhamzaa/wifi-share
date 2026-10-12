import io
import socket
from typing import List, Tuple
from config import PORT, PIN, PIN_ENABLED


def get_local_ip_addresses() -> List[str]:
    """Retrieve all candidate IPv4 non-loopback addresses on the local machine."""
    ips = set()
    # Strategy 1: Connect to a generic routable target via UDP (doesn't send packets)
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.settimeout(0.5)
            s.connect(("10.254.254.254", 1))
            ip = s.getsockname()[0]
            if ip and not ip.startswith("127."):
                ips.add(ip)
    except Exception:
        pass

    # Strategy 2: Check socket gethostbyname_ex
    try:
        hostname = socket.gethostname()
        for ip in socket.gethostbyname_ex(hostname)[2]:
            if not ip.startswith("127."):
                ips.add(ip)
    except Exception:
        pass

    result = list(ips)
    return result if result else ["127.0.0.1"]


def get_primary_ip() -> str:
    """Returns the most likely local network IP address."""
    ips = get_local_ip_addresses()
    # Prefer private network ranges 192.168.x.x, 10.x.x.x, 172.16-31.x.x
    for ip in ips:
        if ip.startswith("192.168.") or ip.startswith("10.") or ip.startswith("172."):
            return ip
    return ips[0]


def print_server_banner(host: str, port: int) -> str:
    """
    Prints an ASCII QR code and clear connection instructions
    for mobile devices on the same local Wi-Fi network.
    """
    primary_ip = get_primary_ip()
    url = f"http://{primary_ip}:{port}"

    print("\n" + "=" * 60)
    print("               WiFi Share Server Started")
    print("=" * 60)
    print(f"[*] Local Network URL: {url}")
    if PIN_ENABLED:
        print(f"[*] Security PIN:      {PIN} (Required for requests)")
    else:
        print("[*] Security PIN:      Disabled (Open on LAN)")

    all_ips = get_local_ip_addresses()
    if len(all_ips) > 1:
        print(f"[*] All Detected IPs:  {', '.join(all_ips)}")

    print("\nScan this QR code with your camera to open:")
    try:
        import qrcode
        qr = qrcode.QRCode(
            version=1,
            error_correction=qrcode.constants.ERROR_CORRECT_L,
            box_size=1,
            border=2,
        )
        qr.add_data(url)
        qr.make(fit=True)
        
        # Render ASCII QR code to terminal
        f = io.StringIO()
        qr.print_ascii(out=f, invert=True)
        print(f.getvalue())
    except Exception as e:
        print(f"   [QR generation skipped: {e}]")

    print("=" * 60 + "\n")
    return url
