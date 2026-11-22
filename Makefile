.PHONY: test-config build-rootfs verify-rootfs test-rootfs test-runtime

test-config:
	python3 distribution/scripts/test-build-config.py

build-rootfs:
	sudo ./distribution/scripts/build-rootfs.sh

verify-rootfs:
	sudo ./distribution/scripts/verify-rootfs.sh

test-rootfs:
	sudo ./distribution/scripts/test-rootfs.sh

test-runtime:
	./android/runtime/test.sh
