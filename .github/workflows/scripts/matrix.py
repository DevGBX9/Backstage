"""
A script to scan through the versions directory and collect all folder names as the subproject list,
then output a json as the github action include matrix
"""
__author__ = 'Fallen_Breath'

import json
import os
import sys


def main():
	target_subproject_env = os.environ.get('TARGET_SUBPROJECT', '')
	target_subprojects = [x for x in target_subproject_env.split(',') if x]
	print('target_subprojects: {}'.format(target_subprojects))

	with open('settings.json') as f:
		settings: dict = json.load(f)

	# only the versions that are actually built are part of the matrix
	build_versions = settings.get('build_versions') or settings['versions']

	if len(target_subprojects) == 0:
		subprojects = build_versions
	else:
		subprojects = []
		for subproject in build_versions:
			if subproject in target_subprojects:
				subprojects.append(subproject)
				target_subprojects.remove(subproject)
		if len(target_subprojects) > 0:
			print('Unexpected subprojects: {}'.format(target_subprojects), file=sys.stderr)
			sys.exit(1)

	matrix_entries = []
	for subproject in subprojects:
		matrix_entries.append({
			'subproject': subproject,
		})
	matrix = {'include': matrix_entries}
	with open(os.environ['GITHUB_OUTPUT'], 'w') as f:
		f.write('matrix={}\n'.format(json.dumps(matrix)))

	print('matrix:')
	print(json.dumps(matrix, indent=2))


if __name__ == '__main__':
	main()
