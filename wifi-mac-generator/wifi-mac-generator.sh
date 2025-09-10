#!/vendor/bin/sh

# wifiaddr:0xXX 0xXX 0xXX 0xXX 0xXX 0xXX
WLANMAC_DAT_PATH="/mnt/vendor/persist/wifimac.dat"

# Intf0MacAddress=XXXXXXXXXXXX
# Intf1MacAddress=XXXXXXXXXXXX
# END
WLAN_MAC_BIN_PATH="/mnt/vendor/persist/wlan_mac.bin"

function wait_for_file() {
    file="${1}"
    max_retries=10
    retries=0

    while [ ! -s "${file}" ]; do
        retries=$((retries + 1))

        if [ "${retries}" -eq "${max_retries}" ]; then
            return 1
        fi

        sleep 1
    done

    return 0
}

if ! wait_for_file "${WLANMAC_DAT_PATH}"; then
    exit
fi

if [ ! -f "${WLAN_MAC_BIN_PATH}" ]; then
    wifiaddr=($(cat "${WLANMAC_DAT_PATH}" | cut -d: -f2))

    first_mac=$(printf "%02X%02X%02X%02X%02X%02X" "${wifiaddr[@]}")

    wifiaddr[5]=$((${wifiaddr[5]} + 1))
    second_mac=$(printf "%02X%02X%02X%02X%02X%02X" "${wifiaddr[@]}")

    echo "Intf0MacAddress=${first_mac}" > "${WLAN_MAC_BIN_PATH}"
    echo "Intf1MacAddress=${second_mac}" >> "${WLAN_MAC_BIN_PATH}"
    echo "END" >> "${WLAN_MAC_BIN_PATH}"
fi
