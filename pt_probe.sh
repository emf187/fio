ls /dev/block/platform/ 2>/dev/null
echo ---
ls /dev/block/ | head -40
echo ---
for i in 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26; do
  name=$(cat /sys/block/mmcblk0/mmcblk0p$i/partname 2>/dev/null)
  echo "p$i: $name"
done
