# Networking
For remote Actors support better performance can be had by adding the following 
to /etc/sysctl.conf:

net.ipv4.tcp_fin_timeout=15
net.ipv4.tcp_slow_start_after_idle=0
