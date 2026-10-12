import re
import socket
import logging
from typing import Optional
from config import PORT, MDNS_SERVICE_NAME, MDNS_SERVICE_TYPE
from services.qr import get_primary_ip

logger = logging.getLogger(__name__)


class DiscoveryService:
    def __init__(self):
        self._zeroconf = None
        self._service_info = None

    def start(self, port: int = PORT) -> bool:
        """Register the WiFi Share service via mDNS (zeroconf)."""
        try:
            from zeroconf import Zeroconf, ServiceInfo, IPVersion

            raw_hostname = socket.gethostname()
            clean_hostname = re.sub(r"[^a-zA-Z0-9-]", "", raw_hostname).lower() or "wifishare"
            primary_ip = get_primary_ip()
            ip_bytes = socket.inet_aton(primary_ip)

            # Ensure proper service name formatting
            type_str = MDNS_SERVICE_TYPE if MDNS_SERVICE_TYPE.endswith(".") else f"{MDNS_SERVICE_TYPE}."
            instance_name = f"{MDNS_SERVICE_NAME} on {clean_hostname}"
            full_service_name = f"{instance_name}.{type_str}"

            self._service_info = ServiceInfo(
                type_=type_str,
                name=full_service_name,
                addresses=[ip_bytes],
                port=port,
                properties={"version": "1.0.0", "path": "/"},
                server=f"{clean_hostname}.local.",
            )

            self._zeroconf = Zeroconf(ip_version=IPVersion.V4Only)
            self._zeroconf.register_service(self._service_info)
            print(f"[mDNS] Active: Advertising as '{instance_name}' ({type_str})")
            return True
        except ImportError:
            print("[mDNS] Note: zeroconf package not installed; mDNS discovery disabled.")
            return False
        except Exception as e:
            # Fallback gracefully if system avahi-daemon or port 5353 has socket restrictions
            print(f"[mDNS] Note: Auto-discovery skipped ({type(e).__name__}). Connect directly via IP: {get_primary_ip()}:{port}")
            return False

    def stop(self):
        """Unregister the mDNS service gracefully."""
        if self._zeroconf and self._service_info:
            try:
                self._zeroconf.unregister_service(self._service_info)
                self._zeroconf.close()
            except Exception:
                pass
            finally:
                self._zeroconf = None
                self._service_info = None


discovery_service = DiscoveryService()
