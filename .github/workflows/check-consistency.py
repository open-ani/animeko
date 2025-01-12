
#  Copyright (C) 2024-2025 OpenAni and contributors.
# 
#  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
#  Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
# 
#  https://github.com/open-ani/ani/blob/main/LICENSE

import sys
import yaml

def main(file1, file2):
	with open(file1, 'r', encoding='utf-8') as f1, \
			open(file2, 'r', encoding='utf-8') as f2:
		data1 = yaml.safe_load(f1)
		data2 = yaml.safe_load(f2)

	if data1 == data2:
		print("YAML files are equivalent.")
		sys.exit(0)
	else:
		print("YAML files differ.")
		sys.exit(1)

if __name__ == "__main__":
	if len(sys.argv) != 3:
		print(f"Usage: {sys.argv[0]} <file1.yml> <file2.yml>")
		sys.exit(2)

	main(sys.argv[1], sys.argv[2])
	