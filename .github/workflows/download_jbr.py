#!/usr/bin/env python3

#  Copyright (C) 2024-2025 OpenAni and contributors.
# 
#  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
#  Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
# 
#  https://github.com/open-ani/ani/blob/main/LICENSE

import os
import sys
import hashlib
import urllib.request

def main():
	# Read environment variables
	runner_tool_cache = os.environ.get("RUNNER_TOOL_CACHE", "")
	jbr_url = os.environ.get("JBR_URL", "")
	jbr_checksum_url = os.environ.get("JBR_CHECKSUM_URL", "")

	if not runner_tool_cache or not jbr_url or not jbr_checksum_url:
		print("Required environment variables (RUNNER_TOOL_CACHE, JBR_URL, JBR_CHECKSUM_URL) are not set.", file=sys.stderr)
		sys.exit(1)

	# Derive filename from the JBR URL
	jbr_filename = jbr_url.split('/')[-1]

	# Compute final JBR path: <runner_tool_cache>/<filename>
	jbr_location = os.path.join(runner_tool_cache, jbr_filename)

	# Write jbrLocation to GITHUB_OUTPUT so subsequent steps can access it
	github_output = os.environ.get("GITHUB_OUTPUT")
	if github_output:
		# Append the variable
		with open(github_output, "a", encoding="utf-8") as f:
			f.write(f"jbrLocation={jbr_location}\n")

	# Function to compute the SHA-512 hash
	def sha512sum(filepath):
		sha512 = hashlib.sha512()
		with open(filepath, "rb") as f:
			for chunk in iter(lambda: f.read(8192), b""):
				sha512.update(chunk)
		return sha512.hexdigest().lower()

	# 1) Download checksum file
	checksum_file = "checksum.tmp"
	urllib.request.urlretrieve(jbr_checksum_url, checksum_file)

	# 2) Parse the first line for the expected checksum
	with open(checksum_file, "r", encoding="utf-8") as cf:
		line = cf.readline().strip()
	expected_checksum = line.split()[0].lower()

	# 3) If jbr_location already exists, compute its SHA-512
	file_checksum = ""
	if os.path.isfile(jbr_location):
		file_checksum = sha512sum(jbr_location)

	# 4) If checksums don’t match, re-download
	if file_checksum != expected_checksum:
		urllib.request.urlretrieve(jbr_url, jbr_location)
		file_checksum = sha512sum(jbr_location)

	# 5) If it still doesn't match, fail
	if file_checksum != expected_checksum:
		print("Checksum verification failed!", file=sys.stderr)
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

	# Optional: Print info about the file
	st = os.stat(jbr_location)
	print(f"Downloaded JBR to: {jbr_location}")
	print(f"File size: {st.st_size} bytes, Last modified: {st.st_mtime}")

if __name__ == "__main__":
	main()
