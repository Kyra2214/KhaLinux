.PHONY: test-config build-rootfs verify-rootfs

test-config:
	python3 distribution/scripts/test-build-config.py

build-rootfs:
	sudo ./distribution/scripts/build-rootfs.sh

verify-rootfs:
	sudo ./distribution/scripts/verify-rootfs.sh
