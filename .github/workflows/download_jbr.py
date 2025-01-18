#  Copyright (C) 2024-2025 OpenAni and contributors.
# 
#  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
#  Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
# 
#  https://github.com/open-ani/ani/blob/main/LICENSE

import hashlib
import os
import ssl
import sys
import urllib.request


def download_with_no_cert_check(url: str, destination: str):
	"""
	Downloads the file from `url` to `destination`,
	ignoring certificate verification errors.
	"""
	# Create an unverified SSL context
	ssl_ctx = ssl.create_default_context()
	ssl_ctx.check_hostname = False
	ssl_ctx.verify_mode = ssl.CERT_NONE

	# Use this context when retrieving the file
	urllib.request.urlretrieve(url, destination, context=ssl_ctx)


def sha512sum(filepath: str) -> str:
	sha512 = hashlib.sha512()
	with open(filepath, "rb") as f:
		for chunk in iter(lambda: f.read(8192), b""):
			sha512.update(chunk)
	return sha512.hexdigest().lower()


def main():
	# Read env variables
	runner_tool_cache = os.environ.get("RUNNER_TOOL_CACHE", "")
	jbr_url = os.environ.get("JBR_URL", "")
	jbr_checksum_url = os.environ.get("JBR_CHECKSUM_URL", "")
	github_output = os.environ.get("GITHUB_OUTPUT", "")

	if not runner_tool_cache or not jbr_url or not jbr_checksum_url:
		print(
			"Missing required env vars: RUNNER_TOOL_CACHE, JBR_URL, JBR_CHECKSUM_URL.",
			file=sys.stderr
		)
		sys.exit(1)

	# Derive final path
	jbr_filename = jbr_url.split('/')[-1]
	jbr_location = os.path.join(runner_tool_cache, jbr_filename)

	# Write jbrLocation to GITHUB_OUTPUT if desired
	if github_output:
		with open(github_output, "a", encoding="utf-8") as f:
			f.write(f"jbrLocation={jbr_location}\n")

	# Download the checksum file (ignoring cert errors)
	checksum_file = "checksum.tmp"
	download_with_no_cert_check(jbr_checksum_url, checksum_file)

	# Parse expected checksum from first line
	with open(checksum_file, "r", encoding="utf-8") as cf:
		line = cf.readline().strip()
	expected_checksum = line.split()[0].lower()

	# If file exists, compute checksum
	file_checksum = ""
	if os.path.isfile(jbr_location):
		file_checksum = sha512sum(jbr_location)

	# If mismatch, redownload
	if file_checksum != expected_checksum:
		download_with_no_cert_check(jbr_url, jbr_location)
		file_checksum = sha512sum(jbr_location)

	# Final check
	if file_checksum != expected_checksum:
		print("[!] Checksum verification failed.", file=sys.stderr)
		try:
			os.remove(checksum_file)
		except OSError:
			pass
		sys.exit(1)

	# Cleanup
	try:
		os.remove(checksum_file)
	except OSError:
		pass

	print("[*] JBR downloaded and verified successfully, ignoring SSL errors.")


if __name__ == "__main__":
	main()
