#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

python3 scripts/quality/verify_debug_mock_network.py --project-root .
./gradlew --no-daemon \
  :app:testDebugUnitTest \
  --tests com.ytone.longcare.network.interceptor.MockRouteRegistryTest \
  --tests com.ytone.longcare.network.interceptor.MockInterceptorTest \
  --tests com.ytone.longcare.network.interceptor.MockFixtureContractTest \
  --tests com.ytone.longcare.di.DebugPhotoCloudUploadModuleTest \
  --tests com.ytone.longcare.di.AppFlavorInterceptorApplierTest \
  --tests com.ytone.longcare.network.interceptor.PerformanceOfflineInterceptorTest \
  --tests com.ytone.longcare.worker.UpdateWorkerTest \
  --tests com.ytone.longcare.worker.StartupUpdateWorkTest \
  :core:data:testDebugUnitTest \
  --tests com.ytone.longcare.network.ApiServiceResultContractTest \
  :feature:location:testDebugUnitTest \
  --tests com.ytone.longcare.features.location.core.DefaultLocationFacadeOfflineTest
