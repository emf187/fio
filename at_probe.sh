cd /data/local/tmp
chmod +rx at_probe.sh 2>/dev/null

probe() {
  CH=$1
  rm -f at$CH.txt
  cat /dev/$CH > at$CH.txt 2>/dev/null &
  P=$!
  sleep 1
  printf 'AT+CGSN\r\n' > /dev/$CH
  sleep 1
  printf 'AT+CEREG=4\r\n' > /dev/$CH
  sleep 0.4
  printf 'AT+CEREG?\r\n' > /dev/$CH
  sleep 1.2
  printf 'AT+CGREG=2\r\n' > /dev/$CH
  sleep 0.3
  printf 'AT+CGREG?\r\n' > /dev/$CH
  sleep 1.2
  printf 'AT+COPS=3,2;+COPS?\r\n' > /dev/$CH
  sleep 1.5
  kill $P 2>/dev/null
  echo "==== $CH ===="
  cat at$CH.txt
  echo "==== end $CH ===="
}

probe stty_w5
probe stty_w2
probe stty_w9
probe stty_w1
