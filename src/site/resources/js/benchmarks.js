/*
 * Jawk
 * Copyright (C) 2006 - 2026 MetricsHub
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
(function() {
	'use strict';

	function installBenchmarkSupport(angular) {
		var siteModule = angular.module('sentry.site');
		var host = document.getElementById('benchmark-application');
		if (host) {
			host.innerHTML = '<jawk-benchmark-report></jawk-benchmark-report>';
		}

		siteModule.component('jawkBenchmarkReport', {
			controller: ['$http', BenchmarkController],
			controllerAs: '$ctrl',
			templateUrl: 'templates/benchmarks.html'
		});
	}

	function BenchmarkController($http) {
		var $ctrl = this;
		var indexUrl = 'benchmarks/index.json';

		$ctrl.loading = true;
		$ctrl.releaseLoading = false;
		$ctrl.error = null;
		$ctrl.releaseError = null;
		$ctrl.releases = [];
		$ctrl.results = [];
		$ctrl.environment = null;

		$ctrl.metric = function(result) {
			return result.primaryMetric || {};
		};

		$ctrl.shortBenchmarkName = function(benchmark) {
			var marker = 'JRTCompare2Benchmark.';
			var index;
			if (!benchmark) {
				return '';
			}
			index = benchmark.indexOf(marker);
			return index >= 0 ? benchmark.substring(index + marker.length) : benchmark;
		};

		$ctrl.forkCount = function(result) {
			var metric = $ctrl.metric(result);
			return metric.rawData ? metric.rawData.length : '';
		};

		$ctrl.releaseKey = function(release) {
			return release.versionPath || release.version;
		};

		$ctrl.releaseLabel = function(release) {
			if (!release) {
				return '';
			}
			return release.date ? release.version + ' (' + release.date + ')' : release.version;
		};

		$ctrl.runnerLabel = function(environment) {
			if (!environment || !environment.runner) {
				return '';
			}
			return [environment.runner.os, environment.runner.arch, environment.runner.name].filter(Boolean).join(' / ');
		};

		$ctrl.systemLabel = function(environment) {
			var label;
			var system;
			if (!environment || !environment.system) {
				return '';
			}
			system = environment.system;
			label = [system.platform, system.release, system.arch].filter(Boolean).join(' / ');
			if (system.cpuCount) {
				label += ' / ' + system.cpuCount + ' CPUs';
			}
			if (system.cpus && system.cpus.length) {
				label += ' / ' + system.cpus.join(', ');
			}
			return label;
		};

		$ctrl.loadRelease = function(release) {
			if (!release) {
				return;
			}

			$ctrl.releaseLoading = true;
			$ctrl.releaseError = null;
			$ctrl.results = [];
			$ctrl.environment = null;

			$http.get(release.jmh, { cache: false }).then(function(response) {
				$ctrl.results = response.data || [];
				$ctrl.results.sort(function(left, right) {
					return left.benchmark.localeCompare(right.benchmark);
				});
			}, function() {
				$ctrl.releaseError = 'Benchmark results could not be loaded for ' + release.version + '.';
			}).finally(function() {
				$ctrl.releaseLoading = false;
			});

			if (release.environment) {
				$http.get(release.environment, { cache: false }).then(function(response) {
					$ctrl.environment = response.data;
				});
			}
		};

		$http.get(indexUrl, { cache: false }).then(function(response) {
			var data = response.data || {};
			var latest = data.latest;
			var releases = data.releases || [];
			var selected = releases.length ? releases[0] : null;
			var index;

			$ctrl.releases = releases;
			for (index = 0; index < releases.length; index++) {
				if (releases[index].version === latest) {
					selected = releases[index];
					break;
				}
			}

			if (!selected) {
				$ctrl.error = 'No published benchmark releases were found in ' + indexUrl + '.';
				return;
			}

			$ctrl.selectedRelease = selected;
			$ctrl.loadRelease(selected);
		}, function() {
			$ctrl.error = 'No benchmark index is published yet. Release benchmarks will create ' + indexUrl + '.';
		}).finally(function() {
			$ctrl.loading = false;
		});
	}

	function showAngularMissing() {
		var host = document.getElementById('benchmark-application');
		if (host) {
			host.innerHTML = '<div class="alert alert-warning">AngularJS is not available, so benchmark results cannot be loaded dynamically.</div>';
		}
	}

	function installWhenReady() {
		if (!window.angular) {
			showAngularMissing();
			return;
		}
		installBenchmarkSupport(window.angular);
	}

	document.addEventListener('DOMContentLoaded', installWhenReady);
}());
