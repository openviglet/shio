shioApp.factory('shGroupFactory', [
	'$uibModal', 'shGroupResource', 'Notification', '$state',
	function ($uibModal, shGroupResource, Notification, $state) {
		const varToString = varObj => Object.keys(varObj)[0];
		return {
			delete: function (shGroup) {
				var modalInstance = this.modalDelete(shGroup);
				modalInstance.result.then(function (removeInstance) {
					var deletedMessage = 'The ' + shGroup.name + ' was deleted.';

					shGroupResource
						.delete({
							id: shGroup.id
						}, function () {
							Notification.error(deletedMessage);
							$state.go('admin.group', {}, { reload: true });
						});
				}, function () {
					// Selected NO
				});
			},
			save: function (shGroup) {
				if (shGroup.id > 0) {
					var updateMessage = 'The ' + shGroup.name + ' was saved.';
					shGroup.$update(function () {
						Notification.warning(updateMessage);
						$state.go('admin.group');
					});
				} else {
					var saveMessage = 'The ' + shGroup.name + ' was updated.';
					delete shGroup.id;
					shGroupResource.save(shGroup, function (response) {
						Notification.warning(saveMessage);
						$state.go('admin.group');
					});
				}
			}, addUsers: function (shGroup) {
				var modalInstance = this.modalSelectUser(shGroup);
				modalInstance.result.then(function (shUsers) {
					if (shGroup.shUsers != null) {
						angular.forEach(shUsers, function (shUser, key) {
							console.log(shUser.username);							
							shGroup.shUsers.push(shUser);
						});
					}
					else {
						shGroup.shUsers = shUsers;
					}
				}, function () {
					// Selected NO
				});
			},
			modalDelete: function (shGroup) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true
					, ariaLabelledBy: 'modal-title'
					, ariaDescribedBy: 'modal-body'
					, templateUrl: 'template/admin/user/user-delete.html'
					, controller: 'ShModalDeleteUserCtrl'
					, controllerAs: varToString({ $ctrl })
					, size: null
					, appendTo: undefined
					, resolve: {
						title: function () {
							return shGroup.name;
						}
					}
				});
			},
			modalSelectUser: function (shGroup) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true
					, ariaLabelledBy: 'modal-title'
					, ariaDescribedBy: 'modal-body'
					, templateUrl: 'template/admin/user/user-select-dialog.html'
					, controller: 'ShModalSelectUserListCtrl'
					, controllerAs: varToString({ $ctrl })
					, size: null
					, appendTo: undefined
					, resolve: {
						groupId: function () {
							return shGroup.id;
						}
					}
				});
			}
		}
	}]);

shioApp.factory('shGroupResource', [ '$resource', 'shAPIServerService', function($resource, shAPIServerService) {
	return $resource(shAPIServerService.get().concat('/v2/group/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

shioApp.controller('ShGroupCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"shGroupResource",
	"shGroupFactory",
	function ($scope, $state, $rootScope, shGroupResource, shGroupFactory) {
		$rootScope.$state = $state;
		$scope.groups = null;
		$scope.groups = shGroupResource.query();

		$scope.groupDelete = function (shGroup) {
			shGroupFactory.delete(shGroup);
		}
	}]);

shioApp.controller('ShGroupEditCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"shGroupResource",
	"shGroupFactory",
	"$stateParams",
	"Notification",
	function ($scope, $state, $rootScope,
		shGroupResource, shGroupFactory, $stateParams, Notification) {
		$rootScope.$state = $state;
		$scope.groupId = $stateParams.groupId;

		$scope.group = shGroupResource.get({
			id: $scope.groupId
		});

		$scope.groupSave = function () {
			angular.forEach($scope.group.shUsers, function (shUser, key) {
				console.log("removendo atributo: " + shUser.username);
				delete shUser.shGroups;									
			});
			$scope.group.$update(function () {
				Notification.warning('The ' + $scope.group.name + ' Group was updated.');
			});
		}

		$scope.addUsers = function () {
			shGroupFactory.addUsers($scope.group);
		}

		$scope.removeUser = function (index) {
			$scope.group.shUsers.splice(index, 1);
		}
	}]);
shioApp.controller('ShGroupNewCtrl', [
	"$scope",
	"$http",
	"$state",
	"$rootScope",
	"shGroupFactory",
	"shAPIServerService",
	function($scope, $http, $state, $rootScope,
			shGroupFactory, shAPIServerService) {
		$rootScope.$state = $state;
		$scope.group = {};
		$scope.$evalAsync($http.get(
				shAPIServerService.get().concat("/v2/group/model")).then(
				function(response) {
					$scope.group = response.data;
				}));
		$scope.groupSave = function() {
			shGroupFactory.save($scope.group);
		}
	} ]);
shioApp.controller('ShModalSelectGroupListCtrl', [
	"$uibModalInstance",
	"username",
	"shGroupResource",
	function ($uibModalInstance, username, shGroupResource) {
		var $ctrl = this;
		$ctrl.username = username;
		$ctrl.checkAll = false;
		$ctrl.shStateObjects = [];
		$ctrl.shObjects = [];
		$ctrl.shGroups = shGroupResource.query({}, function () {
			angular.forEach($ctrl.shGroups, function (shGroup, key) {
				$ctrl.shStateObjects[shGroup.id] = false;
				$ctrl.shObjects[shGroup.id] = shGroup;
			});
		});

		$ctrl.itemSelected = false;
		$ctrl.ok = function () {			
			var objects = [];
            for (var stateKey in $ctrl.shStateObjects) {
				console.log($ctrl.shStateObjects[stateKey]);
                if ($ctrl.shStateObjects[stateKey] === true) {
					objects.push($ctrl.shObjects[stateKey]);
                }
			}
			angular.forEach(objects, function (shGroup, key) {
				console.log(shGroup.name)
			});
			$uibModalInstance.close(objects);
		};

		$ctrl.cancel = function () {
			$ctrl.removeInstance = false;
			$uibModalInstance.dismiss('cancel');
		};

		$ctrl.checkSomeItemSelected = function () {
			$ctrl.itemSelected = false;
			for (var stateKey in $ctrl.shStateObjects) {
				if ($ctrl.shStateObjects[stateKey]) {
					$ctrl.itemSelected = true;
				}
			}
		}
		$ctrl.selectEverything = function () {
			if ($ctrl.checkAll) {
				for (var stateKey1 in $ctrl.shStateObjects) {
					$ctrl.shStateObjects[stateKey1] = true;
				}
			}
			else {
				for (var stateKey2 in $ctrl.shStateObjects) {
					$ctrl.shStateObjects[stateKey2] = false;
				}
			}
			$ctrl.checkSomeItemSelected();
		}
	}]);

shioApp.factory('shRoleFactory', [
	'$uibModal','shRoleResource', 'Notification','$state',
		function($uibModal,shRoleResource, Notification, $state) {
			const varToString = varObj => Object.keys(varObj)[0];
			return {
				delete : function(shRole) {
					var modalInstance = this.modalDelete(shRole);
					modalInstance.result.then(function(removeInstance) {
						var deletedMessage = 'The ' + shRole.name +' was deleted.';
						
						shRoleResource
						.delete({
							id : shRole.id
						},function() {
							Notification.error(deletedMessage);
							$state.go('admin.role',{}, {reload: true});
						});
					}, function() {
						// Selected NO
					});
				}, 			
				save: function(shRole) {
					if (shRole.id > 0 ) {
						var updateMessage = 'The ' + shRole.name +' was saved.';
						shRole.$update(function() {
							Notification.warning(updateMessage);
							$state.go('admin.role');						
						});
					} else {
						var saveMessage = 'The ' + shRole.name +' was updated.';
						delete shRole.id;
						shRoleResource.save(shRole, function(response){
							Notification.warning(saveMessage);
							$state.go('admin.role');
						});
					}	
				},
				modalDelete: function (shRole) {
					var $ctrl = this;
					return $uibModal.open({
						animation: true
						, ariaLabelledBy: 'modal-title'
						, ariaDescribedBy: 'modal-body'
						, templateUrl: 'template/admin/user/user-delete.html'
						, controller: 'ShModalDeleteUserCtrl'
						, controllerAs: varToString({ $ctrl })
						, size: null
						, appendTo: undefined
						, resolve: {
							title: function () {
								return shRole.name;
							}
						}
					});
				}					
			}
		} ]);

shioApp.factory('shRoleResource', [ '$resource', 'shAPIServerService', function($resource, shAPIServerService) {
	return $resource(shAPIServerService.get().concat('/v2/role/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

shioApp.controller('ShRoleCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"shRoleResource",
	"shRoleFactory",
	function ($scope, $state, $rootScope, shRoleResource, shRoleFactory) {
		$rootScope.$state = $state;
		$scope.roles = null;
		$scope.roles = shRoleResource.query();

		$scope.roleDelete = function (shRole) {
			shRoleFactory.delete(shRole);
		}
	}]);

shioApp.controller('ShRoleEditCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"shRoleResource",
	"$stateParams",
	"Notification",
	function ($scope, $state, $rootScope,
		shRoleResource, $stateParams, Notification) {
		$rootScope.$state = $state;
		$scope.roleId = $stateParams.roleId;

		$scope.role = shRoleResource.get({
			id: $scope.roleId
		});

		$scope.roleSave = function () {
			$scope.role.$update(function () {
				Notification.warning('The ' + $scope.role.name + ' Role was updated.');
			});
		}
	}]);
shioApp.controller('ShRoleNewCtrl', [
	"$scope",
	"$http",
	"$state",
	"$rootScope",
	"shRoleFactory",
	"shAPIServerService",
	function($scope, $http, $state, $rootScope,
			shRoleFactory, shAPIServerService) {
		$rootScope.$state = $state;
		$scope.role = {};
		$scope.$evalAsync($http.get(
				shAPIServerService.get().concat("/v2/role/model")).then(
				function(response) {
					$scope.role = response.data;
				}));
		$scope.roleSave = function() {
			shRoleFactory.save($scope.role);
		}
	} ]);
shioApp.controller('ShAdminCtrl', [
	"$rootScope", "$scope", "vigLocale", "$translate", "shAPIServerService", "$http", "$window",
	function ($rootScope, $scope, vigLocale, $translate, shAPIServerService, $http, $window) {
		$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
		$translate.use($scope.vigLanguage);

		if (!$rootScope.authenticated) {
			$http.get(shAPIServerService.get().concat("/v2/user/current"))
				.then(function (response) {
					if (response.data.username) {
						$rootScope.principal = response.data;
						$rootScope.authenticated = true;
					} else {
						$rootScope.authenticated = false;
					}

				}, function () {
					$rootScope.authenticated = false;
				});
		}

		$rootScope.logout = function () {
			$http.post('logout', {}).then(function () {
				$rootScope.authenticated = false;
				$window.location.href = "/";
			}, function (data) {
				$rootScope.authenticated = false;
			});
		}
	}]);

shioApp.factory('shUserFactory', [
	'$uibModal', 'shUserResource', 'Notification', '$state',
	function ($uibModal, shUserResource, Notification, $state) {
		const varToString = varObj => Object.keys(varObj)[0];
		return {
			delete: function (shUser) {
				var modalInstance = this.modalDelete(shUser);
				modalInstance.result.then(function (removeInstance) {
					var deletedMessage = 'The ' + shUser.username + ' was deleted.';

					shUserResource
						.delete({
							id: shUser.username
						}, function () {
							Notification.error(deletedMessage);
							$state.go('admin.user', {}, { reload: true });
						});
				}, function () {
					// Selected NO
				});
			},
			save: function (shUser, isNew) {
				if (!isNew) {
					var updateMessage = 'The ' + shUser.username + ' was updated.';
					shUser.$update(function () {
						Notification.warning(updateMessage);
						$state.go('admin.user');
					});
				} else {
					var saveMessage = 'The ' + shUser.username + ' was saved.';
					shUserResource.save(shUser, function (response) {
						Notification.warning(saveMessage);
						isNew = false;
						$state.go('admin.user');
					});
				}
			},
			addGroups: function (shUser) {
				var modalInstance = this.modalSelectGroup(shUser);
				modalInstance.result.then(function (shGroups) {
					if (shUser.shGroups != null) {
						angular.forEach(shGroups, function (shGroup, key) {
							console.log(shGroup.name);
							shUser.shGroups.push(shGroup);
						});						
					}
					else {
						shUser.shGroups = shGroups;
					}
				}, function () {
					// Selected NO
				});
			}, modalDelete: function (shUser) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true
					, ariaLabelledBy: 'modal-title'
					, ariaDescribedBy: 'modal-body'
					, templateUrl: 'template/admin/user/user-delete.html'
					, controller: 'ShModalDeleteUserCtrl'
					, controllerAs: varToString({ $ctrl })
					, size: null
					, appendTo: undefined
					, resolve: {
						title: function () {
							return shUser.username;
						}
					}
				});
			},
			modalSelectGroup: function (shUser) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true
					, ariaLabelledBy: 'modal-title'
					, ariaDescribedBy: 'modal-body'
					, templateUrl: 'template/admin/group/group-select-dialog.html'
					, controller: 'ShModalSelectGroupListCtrl'
					, controllerAs: varToString({ $ctrl })
					, size: null
					, appendTo: undefined
					, resolve: {
						username: function () {
							return shUser.username;
						}
					}
				});
			}
		}
	}]);

shioApp.factory('shUserResource', [ '$resource', 'shAPIServerService', function($resource, shAPIServerService) {
	return $resource(shAPIServerService.get().concat('/v2/user/:id'), {
		id : '@username'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

shioApp.controller('ShModalDeleteUserCtrl', [
	"$uibModalInstance",
	"title",
	function ($uibModalInstance, title) {
		var $ctrl = this;
		$ctrl.removeInstance = false;
		$ctrl.objectName = title;
		$ctrl.ok = function () {
			$ctrl.removeInstance = true;
			$uibModalInstance.close($ctrl.removeInstance);
		};

		$ctrl.cancel = function () {
			$ctrl.removeInstance = false;
			$uibModalInstance.dismiss('cancel');
		};
	}]);

shioApp.controller('ShModalSelectUserListCtrl', [
	"$uibModalInstance",
	"groupId",
	"shUserResource",
	function ($uibModalInstance, groupId, shUserResource) {
		var $ctrl = this;
		$ctrl.groupId = groupId;
		$ctrl.checkAll = false;
		$ctrl.shStateObjects = [];
		$ctrl.shObjects = [];
		$ctrl.shUsers = shUserResource.query({}, function () {
			angular.forEach($ctrl.shUsers, function (shUser, key) {
				$ctrl.shStateObjects[shUser.username] = false;
				$ctrl.shObjects[shUser.username] = shUser;
			});
		});

		$ctrl.itemSelected = false;
		$ctrl.ok = function () {			
			var objects = [];
            for (var stateKey in $ctrl.shStateObjects) {
				console.log($ctrl.shStateObjects[stateKey]);
                if ($ctrl.shStateObjects[stateKey] === true) {
					console.log("Add");
					objects.push($ctrl.shObjects[stateKey]);
                }
			}
			angular.forEach(objects, function (shUser, key) {
				console.log(shUser.name)
			});
			$uibModalInstance.close(objects);
		};

		$ctrl.cancel = function () {
			$ctrl.removeInstance = false;
			$uibModalInstance.dismiss('cancel');
		};

		$ctrl.checkSomeItemSelected = function () {
			$ctrl.itemSelected = false;
			for (var stateKey in $ctrl.shStateObjects) {
				if ($ctrl.shStateObjects[stateKey]) {
					$ctrl.itemSelected = true;
				}
			}
		}
		$ctrl.selectEverything = function () {
			if ($ctrl.checkAll) {
				for (var stateKey1 in $ctrl.shStateObjects) {
					$ctrl.shStateObjects[stateKey1] = true;
				}
			}
			else {
				for (var stateKey2 in $ctrl.shStateObjects) {
					$ctrl.shStateObjects[stateKey2] = false;
				}
			}
			$ctrl.checkSomeItemSelected();
		}
	}]);

shioApp.controller('ShUserCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"shUserResource",
	"shUserFactory",
	function ($scope, $state, $rootScope, shUserResource, shUserFactory) {
		$rootScope.$state = $state;
		$scope.users = null;
		$scope.users = shUserResource.query();

		$scope.userDelete = function (shUser) {
			shUserFactory.delete(shUser);
		}
	}]);

shioApp.controller('ShUserEditCtrl', [
	"$scope",
	"$http",
	"$window",
	"$stateParams",
	"$state",
	"$rootScope",
	"$translate",
	"vigLocale",
	"shUserResource",
	"shUserFactory",
	function ($scope, $http, $window, $stateParams, $state, $rootScope, $translate, vigLocale, shUserResource, shUserFactory) {
		$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
		$translate.use($scope.vigLanguage);
		$rootScope.$state = $state;
		$scope.userId = $stateParams.userId;

		$scope.user = shUserResource.get({ id: $stateParams.userId });

		$scope.userSave = function () {
			shUserFactory.save($scope.user, false);
		}

		$scope.addGroups = function () {
			shUserFactory.addGroups($scope.user);
		}

		$scope.removeGroup = function (index) {
			$scope.user.shGroups.splice(index, 1);
		}
	}
]);
shioApp.controller('ShUserNewCtrl', [
		"$scope",
		"$http",
		"$state",
		"$rootScope",
		"shUserFactory",
		"shAPIServerService",
		function($scope, $http, $state, $rootScope,
				shUserFactory, shAPIServerService) {
			$rootScope.$state = $state;
			$scope.user = {};
			$scope.isNew = true;
			$scope.$evalAsync($http.get(
					shAPIServerService.get().concat("/v2/user/model")).then(
					function(response) {
						$scope.user = response.data;
					}));
			$scope.userSave = function() {
				shUserFactory.save($scope.user, $scope.isNew);
			}
		} ]);
shioApp.controller('ShCommitCtrl', [
	"$scope",
	"$http",
	"$rootScope",
	"$location",
	"shAPIServerService",
	"$state",
	"$window",
	"$stateParams",
	"vigLocale",
	"$translate",
	"shSiteResource",
	function ($scope, $http, $rootScope, $location, shAPIServerService,
		$state, $window, $stateParams, vigLocale, $translate,
		shSiteResource) {
		$scope.objectId = $stateParams.objectId;
		$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
		$translate.use($scope.vigLanguage);
		$scope.page = 0;
		$scope.shSite = shSiteResource.get({
			id: $stateParams.objectId
		});
		$scope.$evalAsync($http.get(
			shAPIServerService.get().concat(
				"/v2/history/object/" + $scope.objectId + "/0")).then(
					function (response) {
						$scope.commits = response.data
					}));

		$scope.objectPreview = function (shObject) {
			var link = shAPIServerService.get().concat(
				"/v2/object/" + shObject.id + "/preview");
			$window.open(link);
		}
		$scope.loadMore = function () {
			$scope.page = $scope.page + 1;
			$scope.$evalAsync($http.get(
				shAPIServerService.get().concat(
					"/v2/history/object/" + $scope.objectId + "/" + $scope.page)).then(
						function (response) {
							angular.forEach(response.data, function (commit, key) {
								$scope.commits.push(commit);
							});

						}));
		}
	}]);

shioApp.controller('ShComponentExplorerCtrl', [
	'$scope',
	'shAPIServerService',
	'$http',
	'$uibModalInstance',
	'objectId',
	'objectType',
	'shWidgetFileFactory',
	'Upload',
	'$timeout',
	function ($scope, shAPIServerService, $http, $uibModalInstance,
		objectId, objectType, shWidgetFileFactory, Upload, $timeout) {
		var $ctrl = this;
		$ctrl.objectTypeName = "Object";
		$ctrl.folderDoubleClick = false;

		if (angular.equals(objectType, "shFolder")) {
			$ctrl.objectTypeName = "Folder";
			$ctrl.folderDoubleClick = true;
		}
		else if (angular.equals(objectType, "shObject")) {
			$ctrl.objectTypeName = "Content or Folder";
			$ctrl.folderDoubleClick = true;
		}
		$ctrl.enableInsertButton = false;
		$ctrl.shObjectSelected = null;
		$ctrl.ok = function () {
			$uibModalInstance.close($ctrl.shObjectSelected);
		};

		$ctrl.cancel = function () {
			$ctrl.shObjectSelected = null;
			$uibModalInstance.dismiss('cancel');
		};
		$scope.shSite = null;
		$scope.shFolders = null;
		$scope.shPosts = null;
		$scope.breadcrumb = null;
		$scope.isSiteList = false;
		$scope.currentFolderId = null;
		$ctrl.orderAsc = true;
		$ctrl.orderBy = 'name';
		$ctrl.orderByPostSelected = "title";
        $ctrl.orderByFolderSelected = "name";
		$ctrl.shSearchFilter = "";

		// BEGIN Functions
		$ctrl.orderList = function () {
			if ($ctrl.orderAsc) {
				$ctrl.orderAsc = false;
			}
			else {
				$ctrl.orderAsc = true;
			}
			$ctrl.orderBySelection();
		}

		$ctrl.orderBySelection = function () {
            var orderPrefix = "";
            if (!$ctrl.orderAsc) {
                orderPrefix = "-";
            }
            
            if ($ctrl.orderBy === 'name') {
                $ctrl.orderByPostSelected = orderPrefix + "title";
                $ctrl.orderByFolderSelected = orderPrefix + "name";            
            }
            if ($ctrl.orderBy === 'date') {
                $ctrl.orderByPostSelected = orderPrefix + "date";
                $ctrl.orderByFolderSelected = orderPrefix + "date";
            }
		}
		
		$scope.showSearchFolderAction = function () {
			$scope.searchFolder = true;
			focus('search-folder-input');
		}

		$scope.hideSearchFolderAction = function () {
			$scope.searchFolder = false;
			$ctrl.shSearchFilter = "";
		}

		$scope.objectList = function (selectedObjectId) {
			$scope.currentFolderId = selectedObjectId;
			$scope.isSiteList = false;
			$ctrl.enableInsertButton = false;
			$ctrl.shObjectSelected = null;
			var objectListURL = "/v2/object/" + selectedObjectId + "/list/";

			if (!angular.equals(objectType, "shObject")) {
				objectListURL = objectListURL + objectType;
			}

			$scope.$evalAsync($http.get(
				shAPIServerService.get().concat(objectListURL))
				.then(function (response) {
					$scope.shSites = null;
					$scope.shFolders = response.data.shFolders;
					$scope.shPosts = response.data.shPosts;
					$scope.breadcrumb = response.data.breadcrumb;
					$scope.shSite = response.data.shSite;
				}));
		}

		$scope.siteList = function () {
			$scope.isSiteList = true;
			$ctrl.enableInsertButton = false;
			$ctrl.shPostSelected = null;
			$scope.$evalAsync($http.get(
				shAPIServerService.get().concat(
					"/v2/site/"))
				.then(function (response) {
					$scope.shSites = response.data;
					$scope.shFolders = null;
					$scope.shPosts = null;
					$scope.breadcrumb = null;
				}));
		}
		$scope.selectedObject = function (shObject) {
			$ctrl.shObjectSelected = shObject;
			$ctrl.enableInsertButton = true;
		}

		$scope.uploadFile = function (file, errFiles) {
			$scope.f = file;
			$scope.errFile = errFiles && errFiles[0];
			if (file) {
				file.upload = Upload.upload({
					url: shAPIServerService.get().concat('/v2/staticfile/upload'),
					data: {
						file: file,
						folderId: $scope.currentFolderId,
						createPost: true
					}
				});
				file.upload.then(function (response) {
					$timeout(function () {
						console.log("Adicionado o Arquivo");
						$scope.shPosts.unshift(response.data);
						file.result = response.data;
					});
				}, function (response) {
					if (response.status > 0)
						$scope.errorMsg = response.status + ': ' + response.data;
				}, function (evt) {
					file.progress = Math.min(100, parseInt(100.0 *
						evt.loaded / evt.total));
				});
			}
		}
		// END Functions

		$scope.objectList(objectId);

	}]);

shioApp.controller('ShConfigEmailCtrl', [
	"$rootScope", "$state", "$scope",
	function ($rootScope, $state, $scope) {
        $rootScope.$state = $state;
    }]);
shioApp.factory('shAuthProviderFactory', [
	'$uibModal', 'shAuthProviderResource', 'Notification', '$state',
	function ($uibModal, shAuthProviderResource, Notification, $state) {
		const varToString = varObj => Object.keys(varObj)[0];
		return {
			delete: function (shAuthProvider) {
				var modalInstance = this.modalDelete(shAuthProvider);
				modalInstance.result.then(function (removeInstance) {
					var deletedMessage = 'The ' + shAuthProvider.name + ' Auth Provider was deleted.';

					shAuthProviderResource
						.delete({
							id: shAuthProvider.id
						}, function () {
							Notification.error(deletedMessage);
							$state.go('config.auth-providers', {}, { reload: true });
						});
				}, function () {
					// Selected NO
				});
			},
			modalDelete: function (shAuthProvider) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true
					, ariaLabelledBy: 'modal-title'
					, ariaDescribedBy: 'modal-body'
					, templateUrl: 'template/modal/shDeleteGenericObject.html'
					, controller: 'ShModalDeleteGenericObjectCtrl'
					, controllerAs: varToString({ $ctrl })
					, size: null
					, appendTo: undefined
					, resolve: {
						shObjectName: function () {
							return shAuthProvider.name;
						}
					}
				});
			}
		}
	}]);

shioApp.factory('shAuthProviderResource', [ '$resource', 'shAPIServerService', function($resource, shAPIServerService) {
	return $resource(shAPIServerService.get().concat('/v2/provider/auth/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

shioApp.factory('shAuthProviderVendorResource', [ '$resource', 'shAPIServerService', function($resource, shAPIServerService) {
	return $resource(shAPIServerService.get().concat('/v2/provider/auth/vendor/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

shioApp.controller('ShConfigAuthProviderCtrl', [
	"$scope",
	"$state",
	"$stateParams",
	"$rootScope",
	"$http",
	"shAuthProviderResource",
	"shAuthProviderVendorResource",
	"shAuthProviderFactory",
	"shAPIServerService",
	"Notification",
	function ($scope, $state, $stateParams, $rootScope, $http, shAuthProviderResource, shAuthProviderVendorResource, shAuthProviderFactory, shAPIServerService, Notification) {
		$rootScope.$state = $state;
		$scope.authProviderId = $stateParams.authProviderId;
		$scope.isNew = false;
		$scope.vendors = shAuthProviderVendorResource.query();

		if ($scope.authProviderId !== null && typeof $scope.authProviderId !== 'undefined') {
			$scope.authProvider = shAuthProviderResource.get({ id: $scope.authProviderId });
		}
		else {
			$scope.isNew = true;
			$scope
				.$evalAsync($http
					.get(shAPIServerService.get().concat("/v2/provider/auth/model"))
					.then(
						function (response) {
							$scope.authProvider = response.data;
							$scope.authProvider.name = "Untitled Auth Provider"
						}));
		}

		$scope.authProviderSave = function () {
			if ($scope.isNew) {
				shAuthProviderResource.save($scope.authProvider, function (response) {
					$scope.authProvider = response;
					$scope.isNew = false;
					Notification.warning($scope.authProvider.name + '  Auth Provider was saved.');
				});
			}
			else {
				$scope.authProvider.$update(function () {
					Notification.warning('The ' + $scope.authProvider.name + ' Auth Provider was updated.');
				});
			}
		}
		$scope.authProviderSaveAndClose = function () {
			$scope.authProviderSave();
			$state.go('config.auth-providers', {}, { reload: true });
		}

		$scope.removeAuthProvider = function () {
			shAuthProviderFactory.delete($scope.authProvider);
		}

	}]);

shioApp.controller('ShConfigAuthProvidersCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"$http",
	"$filter",
	"shAPIServerService",
	"shAuthProviderResource",
	function ($scope, $state, $rootScope, $http, $filter, shAPIServerService, shAuthProviderResource) {
		$rootScope.$state = $state;

		$scope.authProviders = shAuthProviderResource.query({}, function () {
			$scope.authProviders = $filter('orderBy')($scope.authProviders, 'position');
		});

		$scope.sortableAuthProviders = {
			disabled: false,
			stop: function (e, ui) {
				var sortObject = {};
				angular.forEach($scope.authProviders, function (authProvider, key) {
					sortObject[authProvider.id] = authProvider.position;
				});
				var parameter = JSON.stringify(sortObject);
				$http.put(shAPIServerService.get().concat("/v2/provider/auth/sort"), parameter).then(function (response) { });
			}
		};

		$scope.changeSearchTextBox = function (shSearchFilter) {
			if (shSearchFilter.length > 0) {
				$scope.sortableAuthProviders.disabled = true;
			}
			else {
				$scope.sortableAuthProviders.disabled = false;
			}
		}

	}]);

shioApp.factory('shExchangeProviderFactory', [
	'$uibModal', 'shExchangeProviderResource', 'Notification', '$state',
	function ($uibModal, shExchangeProviderResource, Notification, $state) {
		const varToString = varObj => Object.keys(varObj)[0];
		return {
			delete: function (shExchangeProvider) {
				var modalInstance = this.modalDelete(shExchangeProvider);
				modalInstance.result.then(function (removeInstance) {
					var deletedMessage = 'The ' + shExchangeProvider.name + ' Exchange Provider was deleted.';

					shExchangeProviderResource
						.delete({
							id: shExchangeProvider.id
						}, function () {
							Notification.error(deletedMessage);
							$state.go('config.exchange-providers', {}, { reload: true });
						});
				}, function () {
					// Selected NO
				});
			},
			modalDelete: function (shExchangeProvider) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true
					, ariaLabelledBy: 'modal-title'
					, ariaDescribedBy: 'modal-body'
					, templateUrl: 'template/modal/shDeleteGenericObject.html'
					, controller: 'ShModalDeleteGenericObjectCtrl'
					, controllerAs: varToString({ $ctrl })
					, size: null
					, appendTo: undefined
					, resolve: {
						shObjectName: function () {
							return shExchangeProvider.name;
						}
					}
				});
			}
		}
	}]);

shioApp.factory('shExchangeProviderResource', [ '$resource', 'shAPIServerService', function($resource, shAPIServerService) {
	return $resource(shAPIServerService.get().concat('/v2/provider/exchange/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

shioApp.factory('shExchangeProviderVendorResource', [ '$resource', 'shAPIServerService', function($resource, shAPIServerService) {
	return $resource(shAPIServerService.get().concat('/v2/provider/exchange/vendor/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

shioApp.controller('ShConfigExchangeProviderCtrl', [
	"$scope",
	"$state",
	"$stateParams",
	"$rootScope",
	"$http",
	"shExchangeProviderResource",
	"shExchangeProviderVendorResource",
	"shExchangeProviderFactory",
	"shAPIServerService",
	"Notification",
	function ($scope, $state, $stateParams, $rootScope, $http, shExchangeProviderResource, shExchangeProviderVendorResource, shExchangeProviderFactory, shAPIServerService, Notification) {
		$rootScope.$state = $state;
		$scope.exchangeProviderId = $stateParams.exchangeProviderId;
		$scope.isNew = false;
		$scope.vendors = shExchangeProviderVendorResource.query();

		if ($scope.exchangeProviderId !== null && typeof $scope.exchangeProviderId !== 'undefined') {
			$scope.exchangeProvider = shExchangeProviderResource.get({ id: $scope.exchangeProviderId });
		}
		else {
			$scope.isNew = true;
			$scope
				.$evalAsync($http
					.get(shAPIServerService.get().concat("/v2/provider/exchange/model"))
					.then(
						function (response) {
							$scope.exchangeProvider = response.data;
							$scope.exchangeProvider.name = "Untitled Exchange Provider"
						}));
		}

		$scope.exchangeProviderSave = function () {
			if ($scope.isNew) {
				shExchangeProviderResource.save($scope.exchangeProvider, function (response) {
					$scope.exchangeProvider = response;
					$scope.isNew = false;
					Notification.warning($scope.exchangeProvider.name + '  Exchange Provider was saved.');
				});
			}
			else {
				$scope.exchangeProvider.$update(function () {
					Notification.warning('The ' + $scope.exchangeProvider.name + ' Exchange Provider was updated.');
				});
			}
		}
		$scope.exchangeProviderSaveAndClose = function () {
			$scope.exchangeProviderSave();
			$state.go('config.exchange-providers', {}, { reload: true });
		}

		$scope.removeExchangeProvider = function () {
			shExchangeProviderFactory.delete($scope.exchangeProvider);
		}

	}]);

shioApp.controller('ShConfigExchangeProvidersCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"$http",
	"$filter",
	"shAPIServerService",
	"shExchangeProviderResource",
	function ($scope, $state, $rootScope, $http, $filter, shAPIServerService, shExchangeProviderResource) {
		$rootScope.$state = $state;

		$scope.exchangeProviders = shExchangeProviderResource.query({}, function () {
			$scope.exchangeProviders = $filter('orderBy')($scope.exchangeProviders, 'position');
		});

		$scope.sortableExchangeProviders = {
			disabled: false,
			stop: function (e, ui) {
				var sortObject = {};
				angular.forEach($scope.exchangeProviders, function (exchangeProvider, key) {
					sortObject[exchangeProvider.id] = exchangeProvider.position;
				});
				var parameter = JSON.stringify(sortObject);
				$http.put(shAPIServerService.get().concat("/v2/provider/exchange/sort"), parameter).then(function (response) { });
			}
		};

		$scope.changeSearchTextBox = function (shSearchFilter) {
			if (shSearchFilter.length > 0) {
				$scope.sortableExchangeProviders.disabled = true;
			}
			else {
				$scope.sortableExchangeProviders.disabled = false;
			}
		}

	}]);

shioApp.controller('ShConfigSearchCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	function ($scope, $state, $rootScope) {
		$rootScope.$state = $state;
		
	}]);

shioApp.controller('ShConfigCtrl', [
	"$rootScope", "$scope", "vigLocale", "$translate", "$http", "shAPIServerService", "$window",
	function ($rootScope, $scope, vigLocale, $translate, $http, shAPIServerService, $window) {
		$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
		$translate.use($scope.vigLanguage);

		if (!$rootScope.authenticated) {
			$http.get(shAPIServerService.get().concat("/v2/user/current"))
				.then(function (response) {
					if (response.data.username) {
						$rootScope.principal = response.data;
						$rootScope.authenticated = true;
					} else {
						$rootScope.authenticated = false;
					}

				}, function () {
					$rootScope.authenticated = false;
				});
		}

		$rootScope.logout = function () {
			$http.post('logout', {}).then(function () {
				$rootScope.authenticated = false;
				$window.location.href = "/";
			}, function (data) {
				$rootScope.authenticated = false;
			});
		}
	}]);

shioApp.controller('ShContentCtrl', [
		"$scope",
		"$http",
		"$rootScope",
		"$location",
		"shAPIServerService",
		"$state",
		"$window",
		"$translate",
		"vigLocale",
		function($scope, $http, $rootScope, $location, shAPIServerService,
				$state, $window, $translate, vigLocale) {
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			
			if (!$rootScope.authenticated) {
				$http.get(shAPIServerService.get().concat("/v2/user/current"))
						.then(function(response) {
							if (response.data.username) {
								$rootScope.principal = response.data;
								$rootScope.authenticated = true;
							} else {
								$rootScope.authenticated = false;
								$state.go('content.login');
							}

						}, function() {
							$rootScope.authenticated = false;
							$state.go('content.login');
						});
			}
			$scope.credentials = {};
			$scope.login = function() {
				authenticate($scope.credentials);
			};

			$rootScope.logout = function() {
				$http.post('logout', {}).then(function() {
					$rootScope.authenticated = false;
					$window.location.href = "/";
				}, function(data) {
					$rootScope.authenticated = false;
				});
			}		
		} ]);

shioApp.controller('ShContentHomeCtrl', ["$http", "$scope",
	'vigLocale', "$translate", "shAPIServerService", "shSiteFactory",
	function ($http, $scope, vigLocale, $translate, shAPIServerService, shSiteFactory) {
		$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
		$translate.use($scope.vigLanguage);

		$scope.shWorkflowTasks = [];
		$scope.$evalAsync($http.get(shAPIServerService.get().concat(
			"/v2/workflow/task"))
			.then(
				function (response) {
					$scope.shWorkflowTasks = response.data;
				}));

		$scope.siteExport = function (shSite) {
			shSiteFactory.export(shSite);
		}
		$scope.siteNodeJS = function (shSite) {
			shSiteFactory.nodeJS(shSite);
		}
	}]);

shioApp.controller('ShDevCtrl', [ "$scope", "shAPIDevServerService",
		function($scope, shAPIDevServerService) {
			shAPIDevServerService.set();
		} ]);

shioApp.factory('shEcommercePaymentTypeDefinitionResource', [ '$resource', 'shAPIServerService',
		function($resource, shAPIServerService) {
			return $resource(shAPIServerService.get().concat('/v2/ecom/payment/type/definition/:id'), {
				id : '@id'
			}, {
				update : {
					method : 'PUT'
				}
			});
		} ]);

shioApp.controller('ShECommerceCtrl', [
		"$scope",
		"$http",
		"$rootScope",
		"$location",
		"shAPIServerService",
		"$state",
		"$window",
		"$translate",
		"vigLocale",
		function($scope, $http, $rootScope, $location, shAPIServerService,
				$state, $window, $translate, vigLocale) {
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);	
		} ]);

shioApp.controller('ShECommercePaymentCtrl', [
		"$scope",
		"$http",
		"$rootScope",
		"$location",
		"shAPIServerService",
		"$state",
		"$window",
		"$translate",
		"vigLocale",
		"shEcommercePaymentTypeResource",
		function($scope, $http, $rootScope, $location, shAPIServerService,
				$state, $window, $translate, vigLocale, shEcommercePaymentTypeResource) {
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);	
			$scope.shEcomPaymentTypes = shEcommercePaymentTypeResource.query();
		} ]);

shioApp.controller('ShECommercePaymentNewCtrl', [
		"$scope",
		"$http",
		"$rootScope",
		"$location",
		"shAPIServerService",
		"$state",
		"$window",
		"$translate",
		"vigLocale",
		"shEcommercePaymentTypeDefinitionResource",
		function($scope, $http, $rootScope, $location, shAPIServerService,
				$state, $window, $translate, vigLocale, shEcommercePaymentTypeDefinitionResource) {
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);
			$scope.paymentType = [];
			$scope.$evalAsync($http.get(shAPIServerService.get().concat("/api/v2/ecom/payment/type/model")).then(function (response) {            	 
                $scope.shPost = response.data;
                $scope.shPostType = response.data.shPostType;
            }));
			$scope.paymentTypeDefinitions = shEcommercePaymentTypeDefinitionResource.query();
		} ]);

shioApp.controller('ShECommerceSettingCtrl', [
		"$scope",
		"$http",
		"$rootScope",
		"$location",
		"shAPIServerService",
		"$state",
		"$window",
		"$translate",
		"vigLocale",
		function($scope, $http, $rootScope, $location, shAPIServerService,
				$state, $window, $translate, vigLocale) {
			$scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
			$translate.use($scope.vigLanguage);	
		} ]);

shioApp.factory('shFolderFactory', [
	'$uibModal', 'shFolderResource', 'Notification', '$filter',
	function ($uibModal, shFolderResource, Notification, $filter) {
		const varToString = varObj => Object.keys(varObj)[0];
		return {
			deleteFromList: function (shFolder, shFolders) {
				var modalInstance = this.modalDelete(shFolder);
				modalInstance.result.then(function (removeInstance) {
					var deletedMessage = 'The '
						+ shFolder.name
						+ ' Folder was deleted.';

					shFolderResource
						.delete({
							id: shFolder.id
						}, function () {
							// filter the array
							var foundItem = $filter('filter')(shFolders, { id: shFolder.id }, true)[0];
							// get the index
							var index = shFolders.indexOf(foundItem);
							// remove the item from array
							shFolders.splice(index, 1);
							Notification.error(deletedMessage);
						});
				}, function () {
					// Selected NO
				});
			},			
			modalDelete: function (shFolder) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true
					, ariaLabelledBy: 'modal-title'
					, ariaDescribedBy: 'modal-body'
					, templateUrl: 'template/modal/shDeleteObject.html'
					, controller: 'ShModalDeleteObjectCtrl'
					, controllerAs: varToString({ $ctrl })
					, size: null
					, appendTo: undefined
					, resolve: {
						shObject: function () {
							return shFolder;
						}
					}
				});
			}
		}
	}]);

shioApp.factory('shFolderResource', [ '$resource', 'shAPIServerService',
		function($resource, shAPIServerService) {
			return $resource(shAPIServerService.get().concat('/v2/folder/:id'), {
				id : '@id'
			}, {
				update : {
					method : 'PUT'
				}
			});
		} ]);

shioApp
	.controller(
		'ShFolderEditCtrl',
		[
			"$scope",
			"$http",
			"$state",
			"$stateParams",
			"$rootScope",
			"shFolderResource",
			"shAPIServerService",
			"shFolderFactory",
			"vigLocale",
			"$translate",
			"Notification",
			"shObjectFactory",
			function ($scope, $http, $state, $stateParams,
				$rootScope, shFolderResource,
				shAPIServerService, shFolderFactory, vigLocale,
				$translate, Notification, shObjectFactory) {
				$scope.folderId = $stateParams.folderId;

				$scope.vigLanguage = vigLocale.getLocale()
					.substring(0, 2);
				$translate.use($scope.vigLanguage);
				$scope.shSite = null;
				$scope.shParentFolder = null;
				$scope.shFolder = null;
				$scope.breadcrumb = null;
				$rootScope.$state = $state;
				$scope.shFolder = shFolderResource.get({
					id: $scope.folderId
				});
				var rootFolder = false;
				if ($scope.shFolder.rootFolder == 1) {
					rootFolder = true;
				}

				if (!rootFolder) {
					$scope
						.$evalAsync($http
							.get(
								shAPIServerService
									.get()
									.concat(
										"/v2/folder/"
										+ $scope.folderId
										+ "/path"))
							.then(
								function (response) {
									$scope.shParentFolder = response.data.currentFolder
									$scope.breadcrumb = response.data.breadcrumb;
									$scope.shSite = response.data.shSite;
								}));
				} else {
					$scope.shSite = $scope.shFolder.shSite;
				}
				$scope.folderSave = function () {
					$scope.shFolder
						.$update(function () {
							Notification.warning('The '
								+ $scope.shFolder.name
								+ ' Folder was updated.');
							var parentObjectId = null;
							if ($scope.shFolder.parentFolder == null) {
								parentObjectId = $scope.shSite.id;
							} else {
								parentObjectId = $scope.shFolder.parentFolder.id;
							}
							$state.go('content.children', {
								objectId: parentObjectId
							});
						});
				}
				$scope.openProperties = function () {
					shObjectFactory.openProperties($scope.shFolder);
				}
			}]);

shioApp
		.controller(
				'ShFolderNewCtrl',
				[
						"$scope",
						"$http",
						"$window",
						"$state",
						"$stateParams",
						"$rootScope",
						"shFolderResource",
						"shSiteResource",
						"shAPIServerService",
						'vigLocale',
						'$location',
						'$translate',
						'Notification',
						function($scope, $http, $window, $state, $stateParams,
								$rootScope, shFolderResource,
								shSiteResource, shAPIServerService, vigLocale,
								$location, $translate, Notification) {
							$scope.objectId = $stateParams.objectId;
							$scope.vigLanguage = vigLocale.getLocale()
									.substring(0, 2);
							$translate.use($scope.vigLanguage);
							$scope.shSite = null;
							$scope.shParentFolder = null;
							$scope.shFolder = null;
							$scope.breadcrumb = null;
							$rootScope.$state = $state;
							
							$scope.$evalAsync($http.get(shAPIServerService.get().concat("/v2/object/" + $scope.objectId + "/path")).then(function (response) {
						            $scope.breadcrumb = response.data.breadcrumb;
						            $scope.shSite = response.data.shSite;
						    }));
							  
							$scope.$evalAsync($http.get(
									shAPIServerService.get().concat(
											"/v2/folder/model")).then(
									function(response) {
										$scope.shFolder = response.data;
									}));
							$scope.folderSave = function() {
								if ($scope.shFolder.id != null) {
									$scope.shFolder.$update(function() {
										 Notification.warning('The ' + $scope.shFolder.name +' Folder was created.');
										$state.go('content.children', {folderId: $scope.shFolder.parentFolder.id});
									});
								} else {
									delete $scope.shFolder.id;
									$http.post(
									shAPIServerService.get().concat(
											"/v2/folder/object/" + $scope.objectId), $scope.shFolder).then(
									function(response) {
										$scope.shFolder = response.data;
										$state.go('content.children', {objectId: $scope.objectId});
									});						
								}
							}

						} ]);

shioApp.controller('ShLoginCtrl', [ "$scope", function($scope) {
} ]);

shioApp.factory('ShDialogDeleteFactory', ['$uibModal',
	function ($uibModal) {
		const varToString = varObj => Object.keys(varObj)[0];
		return {
			dialog: function (shObjects) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true,
					ariaLabelledBy: 'modal-title',
					ariaDescribedBy: 'modal-body',
					templateUrl: 'template/dialog/delete/dialog-object-delete.html',
					controller: 'ShDialogDeleteObjectCtrl',
					controllerAs: varToString({ $ctrl }),
					size: null,
					appendTo: undefined,
					resolve: {
						shObjects: function () {
							return shObjects;
						}
					}
				});
			}
		}
	}]);

shioApp.factory('ShDialogImportFromProvider', ['$uibModal',
	function ($uibModal) {
		const varToString = varObj => Object.keys(varObj)[0];
		return {
			dialog: function (objectId, objectType) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true,
					ariaLabelledBy: 'modal-title',
					ariaDescribedBy: 'modal-body',
					templateUrl: 'template/dialog/provider/dialog-provider-select.html',
					controller: 'ShImportFromProviderCtrl',
					controllerAs: varToString({ $ctrl }),
					size: 'lg',
					appendTo: undefined,
					resolve: {
						objectId: function () {
							return objectId;
						},
						objectType: function () {
							return objectType;
						}
					}
				});
			}
		}
	}]);

shioApp.factory('ShDialogSelectObject', ['$uibModal',
	function ($uibModal) {
		const varToString = varObj => Object.keys(varObj)[0];
		return {

			dialog: function (objectId, objectType) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true,
					ariaLabelledBy: 'modal-title',
					ariaDescribedBy: 'modal-body',
					templateUrl: 'template/dialog/select/dialog-object-select.html',
					controller: 'ShComponentExplorerCtrl',
					controllerAs: varToString({ $ctrl }),
					size: 'lg',
					appendTo: undefined,
					resolve: {
						objectId: function () {
							return objectId;
						},
						objectType: function () {
							return objectType;
						}
					}
				});
			}
		}
	}]);

shioApp.controller('ShDialogDeleteObjectCtrl', [
		"$uibModalInstance",
		"shObjects",
		function($uibModalInstance, shObjects) {
			var $ctrl = this;
			$ctrl.removeInstance = false;
			$ctrl.shObjects = shObjects;
		
			$ctrl.ok = function() {
				$uibModalInstance.close();
			};

			$ctrl.cancel = function() {
				$ctrl.removeInstance = false;
				$uibModalInstance.dismiss('cancel');
			};
		} ]);

shioApp.controller('ShModalDeleteGenericObjectCtrl', [
	"$uibModalInstance",
	"shObjectName",
	function ($uibModalInstance, shObjectName) {
		var $ctrl = this;
		$ctrl.removeInstance = false;
		$ctrl.objectName = shObjectName;

		$ctrl.ok = function () {
			$ctrl.removeInstance = true;
			$uibModalInstance.close($ctrl.removeInstance);
		};

		$ctrl.cancel = function () {
			$ctrl.removeInstance = false;
			$uibModalInstance.dismiss('cancel');
		};
	}]);

shioApp.controller('ShModalDeleteObjectCtrl', [
		"$uibModalInstance",
		"$http",
		"shObject",
		"shAPIServerService",
		"shWidgetFileFactory",
		function($uibModalInstance, $http, shObject, shAPIServerService,
				shWidgetFileFactory) {
			var $ctrl = this;
			$ctrl.removeInstance = false;
			$ctrl.shObject = shObject;
			var shObjectType = $ctrl.shObject.objectType;

			if (angular.equals(shObjectType, 'POST')
					|| angular.equals(shObjectType, 'POST_TYPE')) {
				$ctrl.objectName = shObject.title;
			} else {
				$ctrl.objectName = shObject.name;
			}

			$http.get(
					shAPIServerService.get().concat(
							"/v2/reference/to/" + $ctrl.shObject.id))
					.then(function(response) {
						$ctrl.objectRefers = response.data;
					});

			$ctrl.ok = function() {
				$ctrl.removeInstance = true;
				$uibModalInstance.close($ctrl.removeInstance);
			};

			$ctrl.cancel = function() {
				$ctrl.removeInstance = false;
				$uibModalInstance.dismiss('cancel');
			};

			$ctrl.selectFile = function() {
				var modalInstance = shWidgetFileFactory.modalSelectFile($ctrl.shObject.shFolder.id);
				modalInstance.result.then(function(shPostSelected) {
					// Selected Replace and Remove it
					$http.post(
							shAPIServerService.get().concat(
									"/v2/reference/to/" + shObject.id + "/replace/" + shPostSelected.id))
							.then(function(response) {
								$ctrl.objectRefers = response.data;
							});
					
				}, function() {
					// Selected CANCEL
				});
			}
		} ]);

shioApp.factory('shObjectFactory', [
	'$uibModal',
	function ($uibModal) {
		const varToString = varObj => Object.keys(varObj)[0];
		return {
			openProperties: function (shObject) {
				var modalInstance = this.modalProperties(shObject);
				modalInstance.result.then(function (shGroups) {
					shObject.shGroups = shGroups;
				}, function () {
					// Selected NO
				});
			},
			addGroups: function (shObjectGroups) {
				var modalInstance = this.modalSelectGroup(shObjectGroups);
				modalInstance.result.then(function (shGroups) {
					if (shObjectGroups != null) {
						angular.forEach(shGroups, function (shGroup, key) {
							shObjectGroups.push(shGroup.name);
						});
					}
				}, function () {
					// Selected NO
				});
			},
			addUsers: function (shObjectUsers) {
				var modalInstance = this.modalSelectUser(shObjectUsers);
				modalInstance.result.then(function (shUsers) {
					if (shObjectUsers != null) {
						angular.forEach(shUsers, function (shUser, key) {
							shObjectUsers.push(shUser.username);
						});
					}
				}, function () {
					// Selected NO
				});
			},
			modalProperties: function (shObject) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true
					, ariaLabelledBy: 'modal-title'
					, ariaDescribedBy: 'modal-body'
					, templateUrl: 'template/object/object-properties.html'
					, controller: 'ShObjectPropertiesCtrl'
					, controllerAs: varToString({ $ctrl })
					, size: null
					, appendTo: undefined
					, resolve: {
						shObject: function () {
							return shObject;
						}
					}
				});
			},
			modalSelectGroup: function (shObjectGroups) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true
					, ariaLabelledBy: 'modal-title'
					, ariaDescribedBy: 'modal-body'
					, templateUrl: 'template/admin/group/group-select-dialog.html'
					, controller: 'ShModalSelectGroupListCtrl'
					, controllerAs: varToString({ $ctrl })
					, size: null
					, appendTo: undefined
					, resolve: {
						username: function () {
							return shObjectGroups;
						}
					}
				});
			},
			modalSelectUser: function (shObjectUsers) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true
					, ariaLabelledBy: 'modal-title'
					, ariaDescribedBy: 'modal-body'
					, templateUrl: 'template/admin/user/user-select-dialog.html'
					, controller: 'ShModalSelectUserListCtrl'
					, controllerAs: varToString({ $ctrl })
					, size: null
					, appendTo: undefined
					, resolve: {
						groupId: function () {
							return shObjectUsers;
						}
					}
				});
			}
		}
	}]);
shioApp.controller('ShObjectChildrenCtrl', [
    "$scope"
    , "$state"
    , "$stateParams"
    , "$rootScope"
    , "$translate"
    , "$http"
    , "$window"
    , "shAPIServerService"
    , 'vigLocale'
    , "shFolderFactory"
    , "shPostFactory"
    , "ShDialogSelectObject"
    , "ShDialogImportFromProvider"
    , "ShDialogDeleteFactory"
    , "shPostResource"
    , "shFolderResource"
    , "$filter"
    , "Notification"
    , "moment"
    , "shUserResource"
    , "shPostTypeResource"
    , "shSiteFactory"
    , "focus"
    , "shStaticFileUploadFactory"
    , function ($scope, $state, $stateParams, $rootScope, $translate, $http, $window, shAPIServerService, vigLocale, shFolderFactory, shPostFactory, ShDialogSelectObject, ShDialogImportFromProvider, ShDialogDeleteFactory, shPostResource, shFolderResource, $filter, Notification, moment, shUserResource, shPostTypeResource, shSiteFactory, focus, shStaticFileUploadFactory) {
        $scope.loadedObjectList = false;
        $scope.objectId = $stateParams.objectId;
        $scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
        $translate.use($scope.vigLanguage);
        $scope.shCurrentFolder = null;
        $scope.shSite = null;
        $scope.shFolders = null;
        $scope.shPosts = null;
        $scope.breadcrumb = null;
        $rootScope.$state = $state;
        $scope.shStateObjects = [];
        $scope.shObjects = [];
        $scope.actions = [];
        $scope.shUser = null;
        $scope.shLastPostType = null;
        $scope.itemSelected = false;
        $scope.searchFolder = false;
        $scope.shSearchFilter = "";
        $scope.orderAsc = true;
        $scope.orderBy = 'order';
        $scope.orderByPostSelected = null;
        $scope.orderByFolderSelected = null;
        $scope.sortableEnabled = true;


        if ($rootScope.principal == null) {
            $http.get(shAPIServerService.get().concat("/v2/user/current"))
                .then(function (response) {
                    if (response.data.username) {
                        $rootScope.principal = response.data;
                    }
                });
        }

        $scope.publishStatus = function (shPost) {
            if (!shPost.published) {
                return "Unpublished";
            }
            else {
                if (shPost.publishStatus === 'DRAFT') {
                    return "Stale";
                }
                else {
                    return "Published";
                }
            }
        }
        $scope.uploadFiles = function (folderId) {
            var modalInstance = shStaticFileUploadFactory.modalUploadFiles(folderId);
            modalInstance.result.then(function () {
                $scope.loadObjects();
            }, function () {
                // Selected CANCEL
            });
        }
        $scope.checkSomeItemSelected = function () {
            $scope.itemSelected = false;
            for (var stateKey in $scope.shStateObjects) {
                if ($scope.shStateObjects[stateKey]) {
                    $scope.itemSelected = true;
                }
            }
        }

        $scope.showSearchFolderAction = function () {
            $scope.searchFolder = true;
            focus('search-folder-input');
            $scope.sortableFolders.disabled = true;
            $scope.sortablePosts.disabled = true;
        }
        $scope.hideSearchFolderAction = function () {
            $scope.searchFolder = false;
            $scope.shSearchFilter = "";
            $scope.sortableFolders.disabled = false;
            $scope.sortablePosts.disabled = false;
        }
        $scope.siteExport = function () {
            shSiteFactory.export($scope.shSite);
        }

        $scope.orderList = function () {
            if ($scope.orderAsc) {
                $scope.orderAsc = false;
            }
            else {
                $scope.orderAsc = true;
            }
            $scope.orderBySelection();
        }
        $scope.orderBySelection = function () {
            var orderPrefix = "";
            if (!$scope.orderAsc) {
                orderPrefix = "-";
            }
            if ($scope.orderBy === 'order') {
                $scope.orderByPostSelected = null;
                $scope.orderByFolderSelected = null;
                $scope.sortableFolders.disabled = false;
                $scope.sortablePosts.disabled = false;
            }
            if ($scope.orderBy === 'name') {
                $scope.orderByPostSelected = orderPrefix + "title";
                $scope.orderByFolderSelected = orderPrefix + "name";
                $scope.sortableFolders.disabled = true;
                $scope.sortablePosts.disabled = true;
            }
            if ($scope.orderBy === 'description') {
                $scope.orderByPostSelected = orderPrefix + "summary";
                $scope.orderByFolderSelected = orderPrefix + "summary";
                $scope.sortableFolders.disabled = true;
                $scope.sortablePosts.disabled = true;
            }
            if ($scope.orderBy === 'date') {
                $scope.orderByPostSelected = orderPrefix + "date";
                $scope.orderByFolderSelected = orderPrefix + "date";
                $scope.sortableFolders.disabled = true;
                $scope.sortablePosts.disabled = true;
            }
        }
        $scope.sortableFolders = {
            disabled: false,
            stop: function (e, ui) {
                var sortObject = {};               
                angular.forEach($scope.shFolders, function (shFolder, key) {
                    sortObject[shFolder.id] = shFolder.position;
                });
                var parameter = JSON.stringify(sortObject);
                $http.put(shAPIServerService.get().concat("/v2/object/sort"), parameter).then(function (response) { });
            }
        };
        $scope.sortablePosts = {
            disabled: false,
            stop: function (e, ui) {
                var sortObject = {};
                angular.forEach($scope.shPosts, function (shPost, key) {
                    sortObject[shPost.id] = shPost.position;
                });
                var parameter = JSON.stringify(sortObject);
                $http.put(shAPIServerService.get().concat("/v2/object/sort"), parameter).then(function (response) { });
            }
        };
        $scope.shUser = shUserResource.get({
            id: "admin"
            , access_token: $scope.accessToken
        }, function () {
            $scope.shLastPostType = shPostTypeResource.get({
                id: $scope.shUser.lastPostType
            });
        });
        $scope.$evalAsync($http.get(shAPIServerService.get().concat("/v2/history/object/" + $scope.objectId + "/count")).then(function (response) {
            $scope.commits = response.data;
        }));
        $scope.loadObjects = function () {
            $scope.$evalAsync($http.get(shAPIServerService.get().concat("/v2/object/" + $scope.objectId + "/list")).then(function (response) {
                $scope.processResponse(response);
                $scope.loadedObjectList = true;
            }));
        }

        $scope.processResponse = function (response) {
            $scope.shFolders = response.data.shFolders;
            $scope.shFolders = $filter('orderBy')($scope.shFolders, 'position');
            $scope.shPosts = response.data.shPosts;
            $scope.shPosts = $filter('orderBy')($scope.shPosts, 'position');
            $scope.breadcrumb = response.data.breadcrumb;
            $scope.shCurrentFolder = null;
            if ($scope.breadcrumb != null) {
                $scope.shCurrentFolder = $scope.breadcrumb.slice(-1).pop();
            }
            $scope.shSite = response.data.shSite;
            angular.forEach($scope.shFolders, function (shFolder, key) {
                $scope.shStateObjects[shFolder.id] = false;
                $scope.shObjects[shFolder.id] = shFolder;
                $scope.actions[shFolder.id] = false;
            });
            angular.forEach($scope.shPosts, function (shPost, key) {
                $scope.shStateObjects[shPost.id] = false;
                $scope.shObjects[shPost.id] = shPost;
                $scope.actions[shPost.id] = false;
            });
            $scope.itemSelected = false;
        }
        $scope.selectContents = function () {
            for (var stateKey in $scope.shStateObjects) {
                if ($scope.shObjects[stateKey].objectType === "POST") {
                    $scope.shStateObjects[stateKey] = true;
                    $scope.itemSelected = true;
                }
                else {
                    $scope.shStateObjects[stateKey] = false;
                }
            }
            $scope.checkSomeItemSelected();
        }
        $scope.selectFolders = function () {
            for (var stateKey in $scope.shStateObjects) {
                if ($scope.shObjects[stateKey].objectType === "FOLDER") {
                    $scope.shStateObjects[stateKey] = true;
                }
                else {
                    $scope.shStateObjects[stateKey] = false;
                }
            }
            $scope.checkSomeItemSelected();
        }
        $scope.selectEverything = function () {
            for (var stateKey in $scope.shStateObjects) {
                $scope.shStateObjects[stateKey] = true;
            }
            $scope.checkSomeItemSelected();
        }
        $scope.selectNothing = function () {
            for (var stateKey in $scope.shStateObjects) {
                $scope.shStateObjects[stateKey] = false;
            }
            $scope.itemSelected = false;
        }
        $scope.selectInverted = function () {
            for (var stateKey in $scope.shStateObjects) {
                if ($scope.shStateObjects[stateKey]) {
                    $scope.shStateObjects[stateKey] = false;
                }
                else {
                    $scope.shStateObjects[stateKey] = true;
                }
            }
            $scope.checkSomeItemSelected();
        }
        $scope.updateAction = function (id, value) {
            $scope.actions[id] = value;
            $scope.checkSomeItemSelected();
        }
        $scope.isRecent = function (date) {
            var momentDate = new moment(date);
            var now = new moment();
            var duration = moment.duration(momentDate.diff(now))
            if (duration.as('minutes') >= -5) {
                return true;
            }

            return false;
        }
        $scope.objectsCopy = function () {
            var objectGlobalIds = [];
            for (var stateKey in $scope.shStateObjects) {
                if ($scope.shStateObjects[stateKey] === true) {
                    var objectGlobalId = "" + stateKey;
                    objectGlobalIds.push(objectGlobalId);
                }
            }
            $scope.objectsCopyDialog(objectGlobalIds);
        }
        $scope.objectCopy = function (shObject) {
            var objectGlobalIds = [];
            objectGlobalIds.push(shObject.id);
            $scope.objectsCopyDialog(objectGlobalIds);
        }

        $scope.importFromProvider = function (folderId) {
            var modalInstance = ShDialogImportFromProvider.dialog(2000, "shFolder");
            modalInstance.result.then(function (providerItem) {
                console.log(providerItem);
                $http.post(shAPIServerService.get().concat("/v2/provider/exchange/otds/import/" + providerItem.id + "/to/" + folderId)).then(function (response) {
                    var shPost = response.data;
                    $scope.shPosts.push(shPost);
                    Notification.warning(shPost.title + " Asset was imported.");
                });
            }, function () {
                // Selected CANCEL
            });
        }

        $scope.objectsCopyDialog = function (objectGlobalIds) {
            var modalInstance = ShDialogSelectObject.dialog($scope.objectId, "shFolder");
            modalInstance.result.then(function (shObjectSelected) {
                var parameter = JSON.stringify(objectGlobalIds);
                $http.put(shAPIServerService.get().concat("/v2/object/copyto/" + shObjectSelected.id), parameter).then(function (response) {
                    var shObjects = response.data;
                    for (var i = 0; i < shObjects.length; i++) {
                        var shObject = shObjects[i];
                        if (shObjectSelected.id == $scope.objectId) {
                            $scope.shStateObjects[shObject.id] = false;
                            $scope.shObjects[shObject.id] = shObject;
                            $scope.actions[shObject.id] = false;
                        }
                        var copiedMessage = null;
                        if (shObject.objectType == "POST") {
                            if (shObjectSelected.id == $scope.objectId) {
                                $scope.shPosts.push(shObject);
                            }
                            copiedMessage = 'The ' + shObject.title + ' Post was copied.';
                        }
                        else if (shObject.objectType == "FOLDER") {
                            if (shObjectSelected.id == $scope.objectId) {
                                $scope.shFolders.push(shObject);
                            }
                            copiedMessage = 'The ' + shObject.name + ' Folder was copied.';
                        }
                        Notification.warning(copiedMessage);
                    }
                    $scope.checkSomeItemSelected();
                });
            }, function () {
                // Selected CANCEL
            });
        }
        $scope.objectsMove = function () {
            var objectGlobalIds = [];
            for (var stateKey in $scope.shStateObjects) {
                if ($scope.shStateObjects[stateKey] === true) {
                    var objectGlobalId = "" + stateKey;
                    objectGlobalIds.push(objectGlobalId);
                }
            }
            $scope.objectsMoveDialog(objectGlobalIds);
        }
        $scope.objectMove = function (shObject) {
            var objectGlobalIds = [];
            objectGlobalIds.push(shObject.id);
            $scope.objectsMoveDialog(objectGlobalIds);
        }
        $scope.objectsMoveDialog = function (objectGlobalIds) {
            var modalInstance = ShDialogSelectObject.dialog($scope.objectId, "shFolder");
            modalInstance.result.then(function (shObjectSelected) {
                if (shObjectSelected.id == $scope.objectId) {
                    var movedMessageSameFolder = 'No moved, because you selected the same folder as destination';
                    Notification.warning(movedMessageSameFolder);
                }
                else {
                    var parameter = JSON.stringify(objectGlobalIds);
                    $http.put(shAPIServerService.get().concat("/v2/object/moveto/" + shObjectSelected.id), parameter).then(function (response) {
                        var shObjects = response.data;
                        for (var i = 0; i < shObjects.length; i++) {
                            var shObject = shObjects[i];
                            $scope.shStateObjects[shObject.id] = false;
                            var movedMessage = null;
                            if (shObject.objectType == "POST") {
                                movedMessage = 'The ' + shObject.title + ' Post was moved.';
                                var foundItem = $filter('filter')
                                    ($scope.shPosts, {
                                        id: shObject.id
                                    }, true)[0];
                                var index = $scope.shPosts.indexOf(foundItem);
                                $scope.shPosts.splice(index, 1);
                            }
                            else if (shObject.objectType == "FOLDER") {
                                movedMessage = 'The ' + shObject.name + ' Folder was moved.';
                                var foundItemFolder = $filter('filter')
                                    ($scope.shFolders, {
                                        id: shObject.id
                                    }, true)[0];
                                var indexFolder = $scope.shFolders.indexOf(foundItemFolder);
                                $scope.shFolders.splice(indexFolder, 1);
                            }
                            Notification.warning(movedMessage);
                        }
                        $scope.checkSomeItemSelected();
                    });
                }
            }, function () {
                // Selected CANCEL
            });
        }
        $scope.objectClone = function (shObject) {
            var objectGlobalIds = [];
            objectGlobalIds.push(shObject.id);
            var parameter = JSON.stringify(objectGlobalIds);
            var parentObjectId = null;
            if ($scope.shCurrentFolder == null) {
                parentObjectId = $scope.shSite.id;
            }
            else {
                parentObjectId = $scope.shCurrentFolder.id;
            }
            $http.put(shAPIServerService.get().concat("/v2/object/copyto/" + parentObjectId), parameter).then(function (response) {
                var shObjects = response.data;
                for (var i = 0; i < shObjects.length; i++) {
                    shObject = shObjects[i];
                    $scope.shStateObjects[shObject.id] = false;
                    $scope.shObjects[shObject.id] = shObject;
                    $scope.actions[shObject.id] = false;
                    var clonedMessage = null;
                    if (shObject.objectType == "POST") {
                        $scope.shPosts.unshift(shObject);
                        clonedMessage = 'The ' + shObject.title + ' Post was cloned.';
                    }
                    else if (shObject.objectType == "FOLDER") {
                        $scope.shFolders.unshift(shObject);
                        clonedMessage = 'The ' + shObject.name + ' Folder was cloned.';
                    }
                    Notification.warning(clonedMessage);
                }
                $scope.checkSomeItemSelected();
            });
        }

        $scope.objectClearCache = function (shObjectItem) {
            $http.get(shAPIServerService.get().concat("/v2/object/" + shObjectItem.id + "/clear-cache")).then(function (response) {
                var shObject = response.data;
                var clearMessage = null;
                if (shObject.objectType == "POST") {
                    clearMessage = 'The ' + shObject.title + ' Post Cache has been cleared.';
                }
                else if (shObject.objectType == "FOLDER") {
                    clearMessage = 'The ' + shObject.name + ' Folder Cache has been cleared.';
                }
                Notification.warning(clearMessage);
            });
        }

        $scope.objectsRename = function () {
            for (var stateKey in $scope.shStateObjects) {
                if ($scope.shStateObjects[stateKey] === true) {
                    console.log("Rename " + stateKey);
                    $scope.shStateObjects[stateKey] = false;
                }
            }
            $scope.checkSomeItemSelected();
        }
        $scope.objectsDelete = function () {
            var shSelectedObjects = [];
            for (var stateKey in $scope.shStateObjects) {
                if ($scope.shStateObjects[stateKey] === true) {
                    shSelectedObjects.push($scope.shObjects[stateKey]);
                }
            }
            $scope.objectsDeleteDialog(shSelectedObjects);
        }

        $scope.folderDelete = function (shFolder) {
            shFolderFactory.deleteFromList(shFolder, $scope.shFolders);
        }
        $scope.postDelete = function (shPost) {
            shPostFactory.deleteFromList(shPost, $scope.shPosts);
        }
        $scope.objectsDeleteDialog = function (shSelectedObjects) {
            var modalInstance = ShDialogDeleteFactory.dialog(shSelectedObjects);
            modalInstance.result.then(function () {
                angular.forEach(shSelectedObjects, function (value, key) {
                    $scope.shStateObjects[value.id] = false;
                    if (value.objectType === "POST") {
                        var shPost = value;
                        var deletedMessage = 'The ' + shPost.title + ' Post was deleted.';
                        shPostResource.delete({
                            id: shPost.id
                        }, function () {
                            delete $scope.shStateObjects[shPost.id];
                            // filter the array
                            var foundItem = $filter('filter')($scope.shPosts, {
                                id: shPost.id
                            }, true)[0];
                            // get the index
                            var index = $scope.shPosts.indexOf(foundItem);
                            // remove the item from array
                            $scope.shPosts.splice(index, 1);
                            Notification.error(deletedMessage);
                        });
                    }
                    else if (value.objectType === "FOLDER") {
                        var shFolder = value;
                        var deletedMessageFolder = 'The ' + shFolder.name + ' Folder was deleted.';
                        shFolderResource.delete({
                            id: shFolder.id
                        }, function () {
                            delete $scope.shStateObjects[shFolder.id];
                            // filter the array
                            var foundItemFolder = $filter('filter')($scope.shFolders, {
                                id: shFolder.id
                            }, true)[0];
                            // get the index
                            var indexFolder = $scope.shFolders.indexOf(foundItemFolder);
                            // remove the item from array
                            $scope.shFolders.splice(indexFolder, 1);
                            Notification.error(deletedMessageFolder);
                        });
                    }
                    $scope.checkSomeItemSelected();
                });
            }, function () {
                // Selected CANCEL
            });
        }
        $scope.objectPreview = function (shObject) {
            //var link = shAPIServerService.get().concat("/v2/object/" + shObject.id + "/preview");
            var link = "/preview#!/" + shObject.id;
            $window.open(link, "_self");
        }
        $scope.loadObjects();
    }]);
shioApp.controller('ShObjectPropertiesCtrl', [
	"$uibModalInstance",
	"shObject",
	"shObjectFactory",
	"Notification",
	"$http",
	"shAPIServerService",
	function ($uibModalInstance, shObject, shObjectFactory, Notification, $http, shAPIServerService) {
		var $ctrl = this;
		$ctrl.shObject = shObject;
		$http.get(shAPIServerService.get().concat("/v2/object/" + $ctrl.shObject.id + "/security"))
			.then(
				function (response) {
					$ctrl.shSecurity = response.data;
				});

		$ctrl.ok = function () {
			var parameter = JSON.stringify($ctrl.shSecurity);
			$http.put(shAPIServerService.get().concat("/v2/object/" + $ctrl.shObject.id + "/security"), parameter)
				.then(
					function (response) {
						var message = null;
						if ($ctrl.shObject.objectType === 'POST') {
							message = 'The '
								+ $ctrl.shObject.title
								+ ' Post Properties was updated.'
						}
						else if ($ctrl.shObject.objectType === 'FOLDER') {
							message = 'The '
								+ $ctrl.shObject.name
								+ ' Folder Properties was updated.'

						}
						Notification.warning(message);
					});
			$uibModalInstance.close($ctrl.shSecurity);
		};

		$ctrl.cancel = function () {
			$uibModalInstance.dismiss('cancel');
		};

		$ctrl.addConsoleGroups = function () {
			shObjectFactory.addGroups($ctrl.shSecurity.console.shGroups);
		}

		$ctrl.addConsoleUsers = function () {
			shObjectFactory.addUsers($ctrl.shSecurity.console.shUsers);
		}

		$ctrl.removeConsoleGroup = function (index) {
			$ctrl.shSecurity.shGroups.splice(index, 1);
		}

		$ctrl.removeConsoleUser = function (index) {
			$ctrl.shSecurity.shUsers.splice(index, 1);
		}

		$ctrl.addPageGroups = function () {
			shObjectFactory.addGroups($ctrl.shSecurity.page.shGroups);
		}

		$ctrl.removePageGroup = function (index) {
			$ctrl.shSecurity.page.shGroups.splice(index, 1);
		}

	}]);

shioApp.controller('ShPlaygroundCtrl', [
    "$scope", "vigLocale", "$translate",
    function ($scope, vigLocale, $translate) {
        $scope.vigLanguage = vigLocale.getLocale().substring(0, 2);
        $translate.use($scope.vigLanguage);
    }
]);
shioApp.factory('shPostFactory', [
    '$uibModal', 'shPostResource', 'Notification', '$filter', "$state"
    , function ($uibModal, shPostResource, Notification, $filter, $state) {
        const varToString = varObj => Object.keys(varObj)[0];
        return {
            deleteFromList: function (shPost, shPosts) {
                var modalInstance = this.modalDelete(shPost);
                modalInstance.result.then(function (removeInstance) {
                    var deletedMessage = 'The ' + shPost.title + ' Post was deleted.';
                    shPostResource.delete({
                        id: shPost.id
                    }, function () {
                        // filter the array
                        var foundItem = $filter('filter')(shPosts, {
                            id: shPost.id
                        }, true)[0];
                        // get the index
                        var index = shPosts.indexOf(foundItem);
                        // remove the item from array
                        shPosts.splice(index, 1);
                        Notification.error(deletedMessage);
                    });
                }, function () {
                    // Selected NO
                });
            },
            delete: function (shPost) {
                var modalInstance = this.modalDelete(shPost);
                modalInstance.result.then(function (removeInstance) {
                    var deletedMessage = 'The ' + shPost.title + ' Post was deleted.';
                    shPostResource.delete({
                        id: shPost.id
                    }, function () {
                        Notification.error(deletedMessage);
                        $state.go('content.children.folder-children', {
                            folderId: shPost.shFolder.id
                        });
                    });
                }, function () {
                    // Selected NO
                });
            },
            modalDelete: function (shPost) {
                var $ctrl = this;
                return $uibModal.open({
                    animation: true
                    , ariaLabelledBy: 'modal-title'
                    , ariaDescribedBy: 'modal-body'
                    , templateUrl: 'template/modal/shDeleteObject.html'
                    , controller: 'ShModalDeleteObjectCtrl'
                    , controllerAs: varToString({ $ctrl })
                    , size: null
                    , appendTo: undefined
                    , resolve: {
                        shObject: function () {
                            return shPost;
                        }
                    }
                });
            }
        }
    }]);

shioApp.factory('shPostResource', [ '$resource', 'shAPIServerService', function($resource, shAPIServerService) {
	return $resource(shAPIServerService.get().concat('/v2/post/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

shioApp.factory('shPostXPResource', [ '$resource', 'shAPIServerService', function($resource, shAPIServerService) {
	return $resource(shAPIServerService.get().concat('/v2/post/xp/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

shioApp.controller('ShPostEditCtrl', [
	"$scope",
	"$http",
	"$window",
	"$stateParams",
	"$rootScope",
	"shPostResource",
	"shAPIServerService",
	"Notification",
	"$q",
	"shStaticFileFactory",
	"shPostFactory",
	"$filter",
	"shPostXPResource",
	"shObjectFactory",
	function ($scope, $http, $window, $stateParams, $rootScope,
		shPostResource, shAPIServerService, Notification, $q, shStaticFileFactory, shPostFactory, $filter, shPostXPResource, shObjectFactory) {

		$scope.tinymceOptions = {
			plugins: 'shTinyMCE link image code lists paste advlist anchor autolink autoresize charmap codesample directionality emoticons hr insertdatetime legacyoutput media nonbreaking noneditable pagebreak preview print searchreplace tabfocus table template textpattern toc visualblocks visualchars wordcount',
			relative_urls: false,
			toolbar: ' undo redo | bold italic | alignleft aligncenter alignright | code | numlist bullist a11ycheck anchor charmap codesample ltr rtl | link image media shMailTo shAddImage shAddContent | format emoticons  insertdatetime nonbreaking pagebreak paste | preview print searchreplace | table tabledelete | tableprops tablerowprops tablecellprops | tableinsertrowbefore tableinsertrowafter tabledeleterow | tableinsertcolbefore tableinsertcolafter tabledeletecol | template toc visualblocks visualchars wordcount',
			menubar: 'edit insert tools view format table',
			min_height: 500
		};
		$scope.folderId = null;
		$scope.postId = $stateParams.postId;
		$scope.breadcrumb = null;
		$scope.shSite = null;
		$scope.shFolder = null;
		$scope.tabs = [];
		$scope.allowPublish = false;
		$scope.publishStatus = null;
		$scope.shPostXP = shPostXPResource.get({
			id: $scope.postId
		}, function () {
			$scope.shPost = $scope.shPostXP.shPost;
			$scope.allowPublish = $scope.shPostXP.allowPublish;
			$scope.setPublishStatus();

			angular
				.forEach($scope.shPost.shPostAttrs,
					function (shPostAttr, key) {
						// For example: PAYMENT_DEFINITION
						if (angular.equals(shPostAttr.shPostTypeAttr.shWidget.type, "JSON")) {
							shPostAttr.strValue = JSON.parse(shPostAttr.strValue);
						}
					});

			if ($scope.shPost.shFolder != null) {

				$scope.folderId = $scope.shPost.shFolder.id;
				$scope
					.$evalAsync($http
						.get(
							shAPIServerService
								.get()
								.concat(
									"/v2/folder/" + $scope.folderId + "/path")
						)
						.then(
							function (response) {
								$scope.breadcrumb = response.data.breadcrumb;
								$scope.shFolder = response.data.currentFolder;
								$rootScope.shFolder = $scope.shFolder;
								$scope.shSite = response.data.shSite;
							}
						));
			}

			$scope.tabs = [];
			var tabName = $scope.shPost.shPostType.title;

			angular
				.forEach($filter('orderBy')($scope.shPost.shPostAttrs, 'shPostTypeAttr.ordinal', false),
					function (shPostAttr, key) {

						if (shPostAttr.shPostTypeAttr.shWidget.name === 'Tab') {
							tabName = shPostAttr.shPostTypeAttr.label;
							var tab1 = {
								ordinal: shPostAttr.shPostTypeAttr.ordinal,
								name: tabName
							}
							$scope.tabs.push(tab1);

						} else if (key === 0) {
							var tab2 = {
								ordinal: 0,
								name: tabName
							}
							$scope.tabs.push(tab2);
						}
						shPostAttr["tab"] = tabName;
					});

			// /
		});

		$scope.setPublishStatus = function () {
			if (!$scope.shPost.published) {
				$scope.publishStatus = "Unpublished";
			}
			else {
				if ($scope.shPost.publishStatus === 'DRAFT') {
					$scope.publishStatus = "Stale";
				}
				else {
					$scope.publishStatus = "Published";
				}
			}
		}

		$scope.openPreviewURL = function () {
			var link = "/preview#!/" + $scope.shPost.id;
			$window.open(link, "_self");
		}

		$scope.postEditForm = "template/post/form.html";
		$scope.postDelete = function () {
			shPostFactory.delete($scope.shPost);
		}

		var nestedUploadFile = function (promiseFiles, parentOfAttr, postType) {
			if (parentOfAttr.shPostAttrs != null) {
				angular
					.forEach(parentOfAttr.shPostAttrs,
						function (shPostAttrNested, key1) {
							promiseFiles
								.push(uploadFile(
									shPostAttrNested,
									postType));
							nestedUploadFile(promiseFiles, shPostAttrNested, postType);
						});
			}
			else if (parentOfAttr.shChildrenRelatorItems != null) {
				angular
					.forEach(parentOfAttr.shChildrenRelatorItems,
						function (shChildrenRelatorItem, key2) {
							angular
								.forEach(shChildrenRelatorItem.shChildrenPostAttrs,
									function (shChildrenPostAttrNested, key3) {
										promiseFiles
											.push(uploadFile(
												shChildrenPostAttrNested,
												postType));
										nestedUploadFile(promiseFiles, shChildrenPostAttrNested, postType);
									});
						});
			}
		}

		$scope.requestWorkflow = function (publishStatus) {
			console.log("requestWorkflow Process");
			$scope.$evalAsync($http.get(shAPIServerService.get().concat(
				"/v2/object/" + $scope.shPost.id + "/request-workflow/" + publishStatus))
				.then(
					function (response) {
						Notification.warning('Sent the request to publish the ' + $scope.shPost.title + ' Post.');
					}));
		}

		$scope.postSave = function (publishStatus) {
			var promiseFiles = [];
			$scope.filePath = null;
			var postType = $scope.shPost.shPostType;
			nestedUploadFile(promiseFiles, $scope.shPost, postType);

			$q
				.all(promiseFiles)
				.then(
					function (dataThatWasPassed) {
						$scope.shPost.publishStatus = publishStatus;
						shPostResource.update({ id: $scope.shPost.id }, $scope.shPost, function (response) {
							$scope.shPost = response;
							angular
								.forEach($scope.shPost.shPostAttrs,
									function (shPostAttr, key) {
										// For example: PAYMENT_DEFINITION
										if (angular.equals(shPostAttr.shPostTypeAttr.shWidget.type, "JSON")) {
											shPostAttr.strValue = JSON.parse(shPostAttr.strValue);
										}
									});
							// /
							var tabName = $scope.shPost.shPostType.title;
							angular
								.forEach($filter('orderBy')($scope.shPost.shPostAttrs, 'shPostTypeAttr.ordinal', false),
									function (shPostAttr, key) {

										if (shPostAttr.shPostTypeAttr.shWidget.name === 'Tab')
											tabName = shPostAttr.shPostTypeAttr.label;
										shPostAttr["tab"] = tabName;
									});
							// /
							$scope.setPublishStatus();
							Notification.warning('The ' + $scope.shPost.title + ' Post was updated.');
						});
					});

		}

		var uploadFile = function (shPostAttr, postType) {
			var widgetSettingsObject = angular
				.fromJson(shPostAttr.shPostTypeAttr.widgetSettings);

			var shFolderId = $scope.folderId;
			if (widgetSettingsObject != null && widgetSettingsObject.file != null) {
				var folderIdSetting = widgetSettingsObject.file.folderId;
				if (folderIdSetting != null &&
					folderIdSetting.trim().length > 0) {
					shFolderId = folderIdSetting;
				}
			}
			return shStaticFileFactory.uploadFile(
				shFolderId, shPostAttr,
				postType);
		}

		$scope.openProperties = function () {
			shObjectFactory.openProperties($scope.shPost);
		}
	}

]);
shioApp.controller('ShPostFormCtrl', [ "$scope", "$http", "$window",
		"$stateParams", "$state", "$rootScope",
		function($scope, $http, $window, $stateParams, $state, $rootScope) {
			$scope.postTypeAttrId = $stateParams.postTypeAttrId;
			$scope.shPostTypeItem = angular.copy($scope.shPostType);

		} ]);

shioApp.controller('ShPostModalCtrl', [ "$scope", "$http", "$window",
		"$stateParams", "$state", "$rootScope",
		function($scope, $http, $window, $stateParams, $state, $rootScope) {
			$scope.postTypeAttrId = $stateParams.postTypeAttrId;
			$scope.shPostTypeItem = angular.copy($scope.shPostType);

		} ]);

shioApp.controller('ShPostNewCtrl', [
    "$scope", "$http", "$window", "$stateParams", "$rootScope", "shAPIServerService", "shPostResource", "Notification", "$q", "shStaticFileFactory", "$filter",
    function ($scope, $http, $window, $stateParams, $rootScope, shAPIServerService, shPostResource, Notification, $q, shStaticFileFactory, $filter) {
        $scope.tinymceOptions = {
            plugins: 'shTinyMCE link image code lists paste advlist anchor autolink autoresize charmap codesample directionality emoticons hr insertdatetime legacyoutput media nonbreaking noneditable pagebreak preview print searchreplace tabfocus table template textpattern toc visualblocks visualchars wordcount',
            relative_urls: false,
            toolbar: ' undo redo | bold italic | alignleft aligncenter alignright | code | numlist bullist a11ycheck anchor charmap codesample ltr rtl | link image media shMailTo shAddImage shAddContent | format emoticons  insertdatetime nonbreaking pagebreak paste | preview print searchreplace | table tabledelete | tableprops tablerowprops tablecellprops | tableinsertrowbefore tableinsertrowafter tabledeleterow | tableinsertcolbefore tableinsertcolafter tabledeletecol | template toc visualblocks visualchars wordcount',
            menubar: 'edit insert tools view format table',
            min_height: 500
        };
        $scope.folderId = $stateParams.folderId;
        $scope.postTypeId = $stateParams.postTypeId;
        $scope.postTypeName = $stateParams.postTypeName;
        $scope.breadcrumb = null;
        $scope.shPost = null;
        $scope.shFolder = null;
        $scope.shSite = null;
        $scope.shPostType = null;
        $scope.tabs = [];
        $scope.allowPublish = false;

        $scope.$evalAsync($http.get(shAPIServerService.get().concat("/v2/folder/" + $scope.folderId + "/path")).then(function (response) {
            $scope.shFolder = response.data.currentFolder
            $rootScope.shFolder = $scope.shFolder;
            $scope.breadcrumb = response.data.breadcrumb;
            $scope.shSite = response.data.shSite;
        }));
        if ($scope.postTypeId != null) {
            $scope.$evalAsync($http.get(shAPIServerService.get().concat("/v2/post/type/" + $scope.postTypeId + "/post/model")).then(function (response) {
                $scope.shPost = response.data;
                $scope.shPostType = response.data.shPostType;
                if ($scope.shPostType.workflowPublishEntity != null && $scope.shPostType.workflowPublishEntity.trim().length > 0) {
                    $scope.allowPublish = false;
                }
                else {
                    $scope.allowPublish = true;
                }
                ///
                $scope.tabs = [];
                var tabName = $scope.shPost.shPostType.title;

                angular
                    .forEach($filter('orderBy')($scope.shPost.shPostAttrs, 'shPostTypeAttr.ordinal', false),
                        function (shPostAttr, key) {

                            if (shPostAttr.shPostTypeAttr.shWidget.name === 'Tab') {
                                tabName = shPostAttr.shPostTypeAttr.label;
                                var tab1 = {
                                    ordinal: shPostAttr.shPostTypeAttr.ordinal,
                                    name: tabName
                                }
                                $scope.tabs.push(tab1);

                            } else if (key === 0) {
                                var tab2 = {
                                    ordinal: 0,
                                    name: tabName
                                }
                                $scope.tabs.push(tab2);
                            }
                            shPostAttr["tab"] = tabName;
                        });
            }));
        } else {
            $scope.$evalAsync($http.get(shAPIServerService.get().concat("/v2/post/type/name/" + $scope.postTypeName + "/post/model")).then(function (response) {
                $scope.shPost = response.data;
                $scope.shPostType = response.data.shPostType;
            }));

        }
        $scope.postEditForm = "template/post/form.html";
        $scope.openPreviewURL = function () {
            var link = "/preview#!/" + $scope.shPost.id;
            $window.open(link, "_self");
        }

        var nestedUploadFile = function (promiseFiles, parentOfAttr, postType) {
            if (parentOfAttr.shPostAttrs != null) {
                angular
                    .forEach(parentOfAttr.shPostAttrs,
                        function (shPostAttrNested, key1) {
                            promiseFiles
                                .push(uploadFile(
                                    shPostAttrNested,
                                    postType));
                            nestedUploadFile(promiseFiles, shPostAttrNested, postType);
                        });
            } else if (parentOfAttr.shChildrenRelatorItems != null) {
                angular
                    .forEach(parentOfAttr.shChildrenRelatorItems,
                        function (shChildrenRelatorItem, key2) {
                            angular
                                .forEach(shChildrenRelatorItem.shChildrenPostAttrs,
                                    function (shChildrenPostAttrNested, key3) {
                                        promiseFiles
                                            .push(uploadFile(
                                                shChildrenPostAttrNested,
                                                postType));
                                        nestedUploadFile(promiseFiles, shChildrenPostAttrNested, postType);
                                    });
                        });
            }
        }

        var uploadFile = function (shPostAttr, postType) {
            var widgetSettingsObject = angular
                .fromJson(shPostAttr.shPostTypeAttr.widgetSettings);

            var shFolderId = $scope.folderId;
            if (widgetSettingsObject != null && widgetSettingsObject.file != null) {
                var folderIdSetting = widgetSettingsObject.file.folderId;
                if (folderIdSetting != null &&
                    folderIdSetting.trim().length > 0) {
                    shFolderId = folderIdSetting;
                }
            }
            return shStaticFileFactory.uploadFile(
                shFolderId, shPostAttr,
                postType);
        }
        $scope.postSave = function (publishStatus) {
            var promiseFiles = [];
            $scope.filePath = null;
            var postType = $scope.shPost.shPostType;
            nestedUploadFile(promiseFiles, $scope.shPost, postType);

            $q.all(promiseFiles).then(function (dataThatWasPassed) {
                $scope.shPost.publishStatus = publishStatus;
                if ($scope.shPost.id != null) {
                    $scope.shPost.$update(function () {
                        angular
                            .forEach($scope.shPost.shPostAttrs,
                                function (shPostAttr, key) {
                                    if (angular.equals(shPostAttr.shPostTypeAttr.shWidget.type, "JSON")) {
                                        shPostAttr.strValue = JSON.parse(shPostAttr.strValue);
                                    }
                                });
                        ///							
                        var tabName = $scope.shPost.shPostType.title;
                        angular
                            .forEach($filter('orderBy')($scope.shPost.shPostAttrs, 'shPostTypeAttr.ordinal', false),
                                function (shPostAttr, key) {

                                    if (shPostAttr.shPostTypeAttr.shWidget.name === 'Tab')
                                        tabName = shPostAttr.shPostTypeAttr.label;
                                    shPostAttr["tab"] = tabName;
                                });
                        ///  
                        Notification.warning('The ' + $scope.shPost.title + ' Post was update.');
                    });
                } else {
                    delete $scope.shPost.id;
                    $scope.shPost.shFolder = $scope.shFolder;
                    shPostResource.save($scope.shPost, function (response) {
                        $scope.shPost = response;
                        angular
                            .forEach($scope.shPost.shPostAttrs,
                                function (shPostAttr, key) {
                                    if (angular.equals(shPostAttr.shPostTypeAttr.shWidget.type, "JSON")) {
                                        shPostAttr.strValue = JSON.parse(shPostAttr.strValue);
                                    }
                                });
                        ///							
                        var tabName = $scope.shPost.shPostType.title;
                        angular
                            .forEach($filter('orderBy')($scope.shPost.shPostAttrs, 'shPostTypeAttr.ordinal', false),
                                function (shPostAttr, key) {

                                    if (shPostAttr.shPostTypeAttr.shWidget.name === 'Tab')
                                        tabName = shPostAttr.shPostTypeAttr.label;
                                    shPostAttr["tab"] = tabName;
                                });
                        ///  
                        Notification.warning('The ' + $scope.shPost.title + ' Post was created.');
                    });
                }
            });
        }
    }
]);
shioApp.factory('shPostTypeAttrResource', [ '$resource', 'shAPIServerService', function($resource, shAPIServerService) {
	return $resource(shAPIServerService.get().concat('/v2/post/type/attr/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

shioApp.factory('shPostTypeResource', [ '$resource', 'shAPIServerService', function($resource, shAPIServerService) {
	return $resource(shAPIServerService.get().concat('/v2/post/type/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

shioApp.controller('ShPostTypeAttrCtrl', [
		"$scope",
		"$http",
		"$window",
		"$stateParams",
		"$state",
		"$rootScope",
		"shPostTypeAttrResource",
		function($scope, $http, $window, $stateParams, $state, $rootScope,
				shPostTypeAttrResource) {
			$scope.postTypeAttrId = $stateParams.postTypeAttrId;
			$scope.shPostTypeAttr = shPostTypeAttrResource.get({
				id : $scope.postTypeAttrId
			});
			$rootScope.$state = $state;
			$scope.postTypeAttrSave = function() {
				$scope.shshPostTypeAttrPost.$update(function() {
					$state.go('content.post-type-item');
				});
			}

		} ]);

shioApp.controller('ShPostTypeEditorCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"shAPIServerService",
		"shPostTypeResource",
		"Notification",
		function($scope, $http, $window, $state, $rootScope,
				shAPIServerService, shPostTypeResource, Notification) {
			$rootScope.$state = $state;
			$scope.shPostType = null;
			$scope.$evalAsync($http.get(
					shAPIServerService.get().concat("/v2/post/type/model")).then(
					function(response) {
						$scope.shPostType = response.data;
					}));
			$scope.postTypeSave = function() {
				delete $scope.shPostType.id;
				shPostTypeResource.save($scope.shPostType, function() {
					Notification.warning('The ' + $scope.shPostType.name +' Post Type was created.');
					$state.go('content.post-type-select');
				});
			}
		} ]);

shioApp
	.controller(
		'ShPostTypeItemCtrl',
		[
			"$scope",
			"$http",
			"$stateParams",
			"$state",
			"$rootScope",
			"$filter",
			"shWidgetResource",
			"shPostTypeResource",
			"shAPIServerService",
			"Notification",
			function ($scope, $http, $stateParams, $state,
				$rootScope, $filter, shWidgetResource,
				shPostTypeResource,
				shAPIServerService, Notification) {
				$scope.postTypeId = $stateParams.postTypeId;
				$scope.shPostType = null;
				$scope.shWidgets = shWidgetResource.query();
				$rootScope.$state = $state;

				$scope.updateWidgetSettings = function (shPostType) {
					angular.forEach(shPostType.shPostTypeAttrs, function (shPostTypeAttrChild, key) {
						shPostTypeAttrChild.widgetSettings = JSON.stringify(shPostTypeAttrChild.widgetSettingsObject);
						$scope.updateWidgetSettings(shPostTypeAttrChild);
					});
				}
				$scope.updateWidgetSettingsObject = function (shPostType) {
					angular.forEach(shPostType.shPostTypeAttrs, function (shPostTypeAttrChild, key) {
						shPostTypeAttrChild.widgetSettingsObject = angular.fromJson(shPostTypeAttrChild.widgetSettings);
						$scope.updateWidgetSettingsObject(shPostTypeAttrChild);
					});
				}

				shPostTypeResource
					.get({
						id: $scope.postTypeId
					},
						function (response) {
							// $scope.shPostNewItem =
							// angular.copy($scope.shPostType);
							$scope.shPostType = response;
							$scope.shPostType.shPostTypeAttrs = $filter('orderBy')($scope.shPostType.shPostTypeAttrs, 'ordinal');
							$scope.updateWidgetSettingsObject($scope.shPostType);
						});

				$scope.shPostTypeAttrModel = null;

				$scope
					.$evalAsync($http
						.get(
							shAPIServerService
								.get()
								.concat(
									"/v2/post/type/attr/model"))
						.then(
							function (response) {
								$scope.shPostTypeAttrModel = response.data;
							}));

				$scope.postTypeSaveAndClose = function () {
					$scope.shPostType.$update(function () {
						$scope.updateWidgetSettingsObject($scope.shPostType);
						Notification.warning('The ' + $scope.shPostType.name + ' Post Type was updated.');
						$state.go('content.post-type-select');
					});

				}


				$scope.postTypeSave = function () {
					$scope.updateWidgetSettings($scope.shPostType);
					$scope.shPostType.$update(function () {
						$scope.shPostType.shPostTypeAttrs = $filter('orderBy')($scope.shPostType.shPostTypeAttrs, 'ordinal');
						$scope.updateWidgetSettingsObject($scope.shPostType);
						Notification.warning('The ' + $scope.shPostType.name + ' Post Type was updated.');
					});

				}
				$scope.removePostType = function () {
					shPostTypeResource
						.delete({
							id: $scope.shPostType.id
						}, function () {
							Notification.error('The ' + $scope.shPostType.name + ' Post Type was deleted.');
							$state.go('content.post-type-select');
						});
				}

				$scope.addPostTypeAttr = function (shWidget, shPostTypeAttrs) {
					$scope.shPostTypeAttrModel.shWidget = shWidget;

					$scope.shPostTypeAttrModel.ordinal = shPostTypeAttrs.length;
					$scope.shPostTypeAttrModel.label = "Field " + (shPostTypeAttrs.length + 1);
					$scope.shPostTypeAttrModel.name = "FIELD_" + (shPostTypeAttrs.length + 1);
					delete $scope.shPostTypeAttrModel.id;
					shPostTypeAttrs.push(angular
						.copy($scope.shPostTypeAttrModel));
				}

				$scope.postTypeAttrDelete = function (shPostTypeAttrs, shPostTypeAttr, index) {
					var indexItem = shPostTypeAttrs
						.indexOf(shPostTypeAttr);
					shPostTypeAttrs.splice(
						indexItem, 1);

				}
			}
		]);
shioApp.controller('ShPostTypeSelectCtrl', [
	"$scope",
	"$state",
	"$rootScope",
	"$filter",
	"$http",
	"shPostTypeResource",
	"shAPIServerService",
	"Notification",
	function ($scope, $state, $rootScope, $filter, $http, shPostTypeResource, shAPIServerService, Notification) {
		$rootScope.$state = $state;
		$scope.shPostTypes = shPostTypeResource.query(function () {
			angular.forEach($scope.shPostTypes, function (shPostType, key) {
				$scope.shStateObjects[shPostType.id] = false;
				$scope.shObjects[shPostType.id] = shPostType;
				$scope.actions[shPostType.id] = false;
			});
		});
		$scope.shSite = null;
		$scope.breadcrumb = null;
		$scope.shStateObjects = [];
		$scope.shObjects = [];
		$scope.actions = [];
		$scope.itemSelected = false;

		$scope.updateAction = function (id, value) {
			$scope.actions[id] = value;
			$scope.checkSomeItemSelected();
		}

		$scope.checkSomeItemSelected = function () {
			$scope.itemSelected = false;
			for (var stateKey in $scope.shStateObjects) {
				if ($scope.shStateObjects[stateKey]) {
					$scope.itemSelected = true;
				}
			}
		}

		$scope.removePostType = function (shPostType) {
			shPostTypeResource
				.delete({
					id: shPostType.id
				}, function () {
					delete $scope.shStateObjects[shPostType.id];
					// filter the array
					var foundItem = $filter('filter')($scope.shPostTypes, {
						id: shPostType.id
					}, true)[0];
					// get the index
					var index = $scope.shPostTypes.indexOf(foundItem);
					// remove the item from array
					$scope.shPostTypes.splice(index, 1);
					Notification.error('The ' + shPostType.name + ' Post Type was deleted.');
				});
		}

		$scope.postTypeClone = function (shPostType) {
			var postTypeIds = [];
			postTypeIds.push(shPostType.id);
			var parameter = JSON.stringify(postTypeIds);
			$http.put(shAPIServerService.get().concat("/v2/post/type/clone/"), parameter).then(function (response) {
				var shPostTypes = response.data;
				for (var i = 0; i < shPostTypes.length; i++) {
					var shPostTypeNew = shPostTypes[i];
					$scope.shStateObjects[shPostTypeNew.id] = false;
					$scope.shObjects[shPostTypeNew.id] = shPostTypeNew;
					$scope.actions[shPostTypeNew.id] = false;
					$scope.shPostTypes.unshift(shPostTypeNew);

					Notification.warning('The ' + shPostType.title + ' Post Type was cloned.');
				}
				$scope.checkSomeItemSelected();
			});
		}

		$scope.isRecent = function (date) {
            var momentDate = moment(date);
            var now = new moment();
            var duration = moment.duration(momentDate.diff(now))
            if (duration.as('minutes') >= -5) {
                return true;
            }

            return false;
        }
	}]);

shioApp.controller('ShPostTypeSelectWithFolderCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$stateParams",
		"$rootScope",
		"shPostTypeResource",
		"shSiteResource",
		"shAPIServerService",
		function($scope, $http, $window, $state, $stateParams, $rootScope,
				shPostTypeResource, shSiteResource, shAPIServerService) {
			$scope.siteId = $stateParams.siteId;
			$scope.folderId = $stateParams.folderId;
			$rootScope.$state = $state;
			$scope.shPostTypes = shPostTypeResource.query();
			$scope.shSite = null;
			$scope.breadcrumb = null;
			if ($scope.folderId != null) {
				$scope.$evalAsync($http.get(
						shAPIServerService.get().concat(
								"/v2/folder/" + $scope.folderId + "/path"))
						.then(function(response) {
							$scope.breadcrumb = response.data.breadcrumb;
							$scope.shSite = response.data.currentFolder.shSite;
						}));
			} else {
				$scope.shSite = shSiteResource.get({
					id : $scope.siteId
				});
			}
		} ]);

shPreviewApp.controller('ShPreviewCtrl', [
	"$scope",
	"$state",
	"$stateParams",
	"$sce",
	"shAPIServerService",
	"$http",
	"$window",
	"$timeout",
	"$document",
	function ($scope, $state, $stateParams, $sce, shAPIServerService, $http,
		$window, $timeout, $document) {
		angular.element('#myiframe').load(function () {
			console.log(angular.element('#myiframe')[0].contentDocument.URL);

		});
		$scope.objectId = $stateParams.objectId;
		$scope.objectInfo = null;
		$scope.actionButton = null;
		$scope.returnButton = null;
		$scope.captionButton = "Loading...";
		$scope.objectType = null;
		$scope.message = "";
		$scope.alert = false;
		$scope.refreshIframe = false;
		$scope.$evalAsync($http.get(
			shAPIServerService.get().concat(
				"/v2/object/" + $scope.objectId + "/path")).then(
					function (response) {
						$scope.objectInfo = response.data;
						if ($scope.objectInfo.shPost == null) {
							if ($scope.objectInfo.currentFolder == null) {
								if ($scope.objectInfo.shSite != null) {
									$scope.captionButton = "Edit the Site";
									$scope.actionButton = "/content#!/site/"
										+ $scope.objectId + "/edit";
									$scope.returnButton = "/content#!/list/"
										+ $scope.objectId;
									$scope.objectType = "SITE";
								}
							} else {
								$scope.captionButton = "Edit the Folder";
								$scope.actionButton = "/content#!/folder/"
									+ $scope.objectId + "/edit";

								if ($scope.objectInfo.currentFolder.parentFolder != null) {
									$scope.returnButton = "/content#!/list/"
										+ $scope.objectInfo.currentFolder.parentFolder.id;
								}
								else {
									$scope.returnButton = "/content#!/list/"
										+ $scope.objectInfo.shSite.id;
								}
								$scope.objectType = "FOLDER";
							}
						} else {
							$scope.captionButton = "Edit the Post";
							$scope.actionButton = "/content#!/post/type/"
								+ $scope.objectInfo.shPost.shPostType.id
								+ "/post/" + $scope.objectId;
							$scope.returnButton = "/content#!/list/"
								+ $scope.objectInfo.shPost.shFolder.id;
							$scope.objectType = "POST";
						}
					}));
		$scope.previewURL = $sce
			.trustAsResourceUrl(shAPIServerService.get().concat(
				"/v2/object/" + $scope.objectId + "/preview"));
		$scope.isMobile = false;

		$scope.openNewWindow = function () {
			$window.open($scope.previewURL, "_blank");
		}

		$scope.actionLink = function () {
			$window.open($scope.actionButton, "_self");
		}
		$scope.returnLink = function () {
			$window.open($scope.returnButton, "_self");
		}

		$scope.objectClearCache = function () {
			$http.get(shAPIServerService.get().concat("/v2/object/" + $scope.objectId + "/clear-cache")).then(function (response) {				
				var clearMessage = null;
				if ($scope.objectType == "POST") {
					clearMessage = 'The Post Cache has been cleared.';
				}
				else if ($scope.objectType == "FOLDER") {
					clearMessage = 'The Folder Cache has been cleared.';
				}
				$scope.message = clearMessage;
				$scope.alert = true;
				$scope.resfeshIframe();
				$timeout(function () {
					$scope.alert = false;
					$scope.message = "";

				}, 2000);
			});
		}

		$scope.resfeshIframe = function () {
			$scope.previewURL = $scope.previewURL +"?a=1";
			//$scope.refreshIframe = true;
		/*	$timeout(function () {
				$scope.refreshIframe = false;
			}, 2000);
			*/
		};
		

	}]);
shioApp.controller('ShImportFromProviderCtrl', [
	'$scope',
	'shAPIServerService',
	'$http',
	'$uibModalInstance',
	'objectType',
	function ($scope, shAPIServerService, $http, $uibModalInstance, objectType) {
		var $ctrl = this;
		$ctrl.loadData = false;
		$ctrl.objectTypeName = "Object";
		$ctrl.folderDoubleClick = false;

		if (angular.equals(objectType, "shFolder")) {
			$ctrl.objectTypeName = "Folder";
			$ctrl.folderDoubleClick = true;
		}
		else if (angular.equals(objectType, "shObject")) {
			$ctrl.objectTypeName = "Content or Folder";
			$ctrl.folderDoubleClick = true;
		}
		$ctrl.enableInsertButton = false;
		$ctrl.shObjectSelected = null;
		$ctrl.ok = function () {
			$uibModalInstance.close($ctrl.shObjectSelected);
		};

		$ctrl.cancel = function () {
			$ctrl.shObjectSelected = null;
			$uibModalInstance.dismiss('cancel');
		};

		$scope.isProviderList = false;
		$scope.currentFolderId = null;
		$ctrl.orderAsc = true;
		$ctrl.orderBy = 'name';
		$ctrl.orderByPostSelected = "title";
		$ctrl.orderByFolderSelected = "name";
		$ctrl.shSearchFilter = "";

		// BEGIN Functions
		$ctrl.orderList = function () {
			if ($ctrl.orderAsc) {
				$ctrl.orderAsc = false;
			}
			else {
				$ctrl.orderAsc = true;
			}
			$ctrl.orderBySelection();
		}

		$ctrl.orderBySelection = function () {
			var orderPrefix = "";
			if (!$ctrl.orderAsc) {
				orderPrefix = "-";
			}

			if ($ctrl.orderBy === 'name') {
				$ctrl.orderByPostSelected = orderPrefix + "title";
				$ctrl.orderByFolderSelected = orderPrefix + "name";
			}
			if ($ctrl.orderBy === 'date') {
				$ctrl.orderByPostSelected = orderPrefix + "date";
				$ctrl.orderByFolderSelected = orderPrefix + "date";
			}
		}

		$scope.showSearchFolderAction = function () {
			$scope.searchFolder = true;
			focus('search-folder-input');
		}

		$scope.hideSearchFolderAction = function () {
			$scope.searchFolder = false;
			$ctrl.shSearchFilter = "";
		}

		$scope.rootList = function (providerId) {
			$scope.providerId = providerId;
			$scope.objectList("_root");
		}

		$scope.objectList = function (folderId) {
			if (!$ctrl.loadData) {
				$ctrl.loadData = true;
				delete $ctrl.providers;
				$scope.isProviderList = false;
				$scope.currentFolderId = folderId;
				$ctrl.enableInsertButton = false;
				$ctrl.shObjectSelected = null;
				var objectListURL = "/v2/provider/exchange/" + $scope.providerId + "/" + folderId + "/list/";

				$scope.$evalAsync($http.get(
					shAPIServerService.get().concat(objectListURL))
					.then(function (response) {
						$ctrl.providerItem = response.data;
						$ctrl.loadData = false;
					}));
			}
		}

		$scope.providerList = function () {
			if (!$ctrl.loadData) {
				$ctrl.loadData = true;
				delete $ctrl.providerItem;
				$scope.isProviderList = true;
				$ctrl.enableInsertButton = false;
				$ctrl.shPostSelected = null;
				$scope.$evalAsync($http.get(
					shAPIServerService.get().concat(
						"/v2/provider/exchange/"))
					.then(function (response) {
						$ctrl.providers = response.data;
						$ctrl.loadData = false;
					}));
			}
		}
		$scope.selectedObject = function (shObject) {
			$ctrl.shObjectSelected = shObject;
			$ctrl.enableInsertButton = true;
		}

		// END Functions

		$scope.providerList();
	}]);

shioApp.controller('ShSearchCtrl', [
		"$scope",
		"$http",
		"$state",
		"$stateParams",
		"$location",
		"$rootScope",
		"shAPIServerService",
		function($scope, $http, $state, $stateParams, $location, $rootScope,
				shAPIServerService) {
			$scope.shQuery = $stateParams.query;
			$rootScope.$state = $state;
			$scope.shPosts = null;
			$scope.$evalAsync($http.get(
					shAPIServerService.get().concat(
							"/v2/search?q=" + $scope.shQuery)).then(
					function(response) {
						$scope.shPostsWithBreadCrumb = response.data;
					}));
		} ]);

shioApp.controller('ShSearchTypeCtrl', [
		"$scope",
		"$http",
		"$state",
		"$stateParams",
		"$location",
		"$rootScope",
		"shAPIServerService",
		function($scope, $http, $state, $stateParams, $location, $rootScope,
				shAPIServerService) {
			$scope.type = $stateParams.type;
			$rootScope.$state = $state;
			$scope.shPosts = null;
			$scope.$evalAsync($http.get(
					shAPIServerService.get().concat(
							"/v2/search/type/" + $scope.type)).then(
					function(response) {
						$scope.shPostsWithBreadCrumb = response.data;
					}));
		} ]);

shioApp.factory('shSiteFactory', [
	'$uibModal', 'shSiteResource', 'shAPIServerService', 'Notification', '$state',
	function ($uibModal, shSiteResource, shAPIServerService, Notification, $state) {
		const varToString = varObj => Object.keys(varObj)[0];
		return {
			export: function (shSite) {
				window.open(shAPIServerService
					.get()
					.concat(
						"/v2/site/" + shSite.id + "/export"), '_self', '');
			},
			nodeJS: function (shSite) {
				window.open(shAPIServerService
					.get()
					.concat(
						"/v2/site/" + shSite.id + "/nodejs"), '_self', '');
			},
			delete: function (shSite) {
				var modalInstance = this.modalDelete(shSite);
				modalInstance.result.then(function (removeInstance) {
					var deletedMessage = 'The ' + shSite.name + ' Site was deleted.';

					shSiteResource
						.delete({
							id: shSite.id
						}, function () {
							Notification.error(deletedMessage);
							$state.go('content', {}, { reload: true });
						});
				}, function () {
					// Selected NO
				});
			},
			save: function (shSite) {
				if (shSite.id != null) {
					var updateMessage = 'The ' + shSite.name + ' Site was updated.';
					shSite.$update(function (response) {
						Notification.warning(updateMessage);
						$state.go('content.children', { objectId: response.id });
					});
				} else {
					var saveMessage = 'The ' + shSite.name + ' Site was saved.';
					delete shSite.id;
					shSiteResource.save(shSite, function (response) {
						Notification.warning(saveMessage);
						$state.go('content.children', { objectId: response.id });
					});
				}
			},
			modalDelete: function (shSite) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true
					, ariaLabelledBy: 'modal-title'
					, ariaDescribedBy: 'modal-body'
					, templateUrl: 'template/modal/shDeleteObject.html'
					, controller: 'ShModalDeleteObjectCtrl'
					, controllerAs: varToString({ $ctrl })
					, size: null
					, appendTo: undefined
					, resolve: {
						shObject: function () {
							return shSite;
						}
					}
				});
			}
		}
	}]);

shioApp.factory('shSiteResource', [ '$resource', 'shAPIServerService', function($resource, shAPIServerService) {
	return $resource(shAPIServerService.get().concat('/v2/site/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

shioApp.controller('ShSiteEditCtrl', [
		"$scope",
		"$state",
		"$stateParams",
		"shSiteResource",
		"shSiteFactory",
		function($scope, $state, $stateParams, shSiteResource, shSiteFactory) {
			$scope.shSite = shSiteResource.get({id: $stateParams.siteId});
			$scope.siteSave = function() {
				shSiteFactory.save($scope.shSite);
			}

			$scope.siteExport = function() {
				shSiteFactory.export($scope.shSite);
			}
			
			$scope.siteDelete = function() {
				shSiteFactory.delete($scope.shSite);
			}

		} ]);

shioApp.controller('ShSiteImportCtrl', [
		'$scope',
		'$state',
		'Upload',
		'$timeout',
		'shAPIServerService',
		'Notification',
		function($scope, $state, Upload, $timeout, shAPIServerService,
				Notification) {
			$scope.shImport = {
				file : null
			};

			$scope.$watch('shImport.file', function() {
				//
			});

			$scope.clearFile = function() {
				$scope.shImport.file = null;
			}
			$scope.importFile = function() {
				if (!$scope.shImport.file.$error) {
					Upload.upload({
						url : shAPIServerService.get().concat('/v2/import'),
						data : {
							file : $scope.shImport.file
						}
					}).then(

							function(resp) {
								if (typeof resp.data.sites != 'undefined') {
									var siteName = resp.data.sites[0].name;
									Notification.warning('The ' + siteName
											+ ' Site was imported.');
								} else {
									Notification
											.warning('Objects were imported.');
								}
								$state.go('content');
							},
							null,
							function(evt) {
							//	var progressPercentage = parseInt(100.0
							//			* evt.loaded / evt.total);
							});
				}
			}
		} ]);

shioApp.controller('ShSiteListCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$rootScope",
	"shSiteResource",
	function ($scope, $http, $window, $state, $rootScope, shSiteResource) {
		$rootScope.$state = $state;
		$scope.shStateObjects = [];
		$scope.shObjects = [];
		$scope.actions = [];
		$scope.itemSelected = false;
		$scope.shSites = shSiteResource.query(function () {
			angular.forEach($scope.shSites, function (shSite, key) {
				$scope.shStateObjects[shSite.id] = false;
				$scope.shObjects[shSite.id] = shSite;
				$scope.actions[shSite.id] = false;
			});
		});

		$scope.objectPreview = function (shObject) {
			var link = "/preview#!/" + shObject.id;
			$window.open(link, "_self");
		}

		$scope.updateAction = function (id, value) {
			$scope.actions[id] = value;
			$scope.checkSomeItemSelected();
		}

		$scope.checkSomeItemSelected = function () {
			$scope.itemSelected = false;
			for (var stateKey in $scope.shStateObjects) {
				if ($scope.shStateObjects[stateKey]) {
					$scope.itemSelected = true;
				}
			}
		}

		$scope.isRecent = function (date) {
            var momentDate = moment(date);
            var now = new moment();
            var duration = moment.duration(momentDate.diff(now))
            if (duration.as('minutes') >= -5) {
                return true;
            }

            return false;
        }

	}]);

shioApp.controller('ShSiteNewCtrl', [
	"$scope",
	"$http",
	"shAPIServerService",
	"shSiteFactory",
	"$state",
	function ($scope, $http, shAPIServerService, shSiteFactory, $state) {
		$scope.shSite = null;
		$scope.$evalAsync($http.get(
			shAPIServerService.get().concat("/v2/site/model")).then(
				function (response) {
					$scope.shSite = response.data;
				}));
		$scope.siteSave = function () {
			shSiteFactory.save($scope.shSite);

		}

	}]);

shioApp.factory('shStaticFileFactory', [
    '$q', 'Upload', 'shAPIServerService', '$timeout',
    function ($q, Upload, shAPIServerService) {
        return {
            uploadFile: function (folderId, shPostAttr, postType) {
                var deferredFile = $q.defer();
                if (shPostAttr.shPostTypeAttr.shWidget != null && shPostAttr.shPostTypeAttr.shWidget.name == "File" && shPostAttr.file != null) {
                    var createPost = true;
                    if (postType.name == "File") {
                        createPost = false;
                    }
                    var file = shPostAttr.file;
                    if (!file.$error) {
                        Upload.upload({
                            url: shAPIServerService.get().concat('/v2/staticfile/upload'),
                            data: {
                                file: file,
                                folderId: folderId,
                                createPost: createPost
                            }
                        }).then(function (resp) {
                            if (createPost) {
                                shPostAttr.strValue = resp.data.id;
                                delete shPostAttr.referenceObject;
                            } else {
                                var filePath = resp.data.title;
                                shPostAttr.strValue = filePath;
                            }
                            deferredFile.resolve("Success");
                        }, null, function (evt) {
                            //var progressPercentage = parseInt(100.0 * evt.loaded / evt.total);
                        });
                    }
                } else {
                    deferredFile.resolve("Success");
                }
                return deferredFile.promise;
            }
        }
    }
]);
shioApp.factory('shStaticFileUploadFactory', [ '$uibModal',
		function($uibModal) {
			const varToString = varObj => Object.keys(varObj)[0];
			return {
				modalUploadFiles : function(folderId) {
					var $ctrl = this;
					return $uibModal.open({
						animation : true,
						ariaLabelledBy : 'modal-title',
						ariaDescribedBy : 'modal-body',
						templateUrl : 'template/staticfile/staticfile-upload.html',
						controller : 'ShStaticFileUploadSelectCtrl',
						controllerAs : varToString({ $ctrl }),
						size : null,
						appendTo : undefined,
						resolve : {
							folderId : function() {
								return folderId;
							}
						}
					});
				}
			}
		} ]);

shioApp.controller('ShStaticFileUploadCtrl', [
		"$uibModalInstance",
		"$http",
		"shObject",
		"shAPIServerService",
		"shStaticFileUploadFactory",
		function($uibModalInstance, $http, shObject, shAPIServerService,
			shStaticFileUploadFactory) {
			var $ctrl = this;
			$ctrl.removeInstance = false;
			$ctrl.shObject = shObject;
			var shObjectType = $ctrl.shObject.objectType;

			if (angular.equals(shObjectType, 'POST')
					|| angular.equals(shObjectType, 'POST_TYPE')) {
				$ctrl.objectName = shObject.title;
			} else {
				$ctrl.objectName = shObject.name;
			}

			$http.get(
					shAPIServerService.get().concat(
							"/v2/reference/to/" + $ctrl.shObject.id))
					.then(function(response) {
						$ctrl.objectRefers = response.data;
					});

			$ctrl.ok = function() {
				$ctrl.removeInstance = true;
				$uibModalInstance.close($ctrl.removeInstance);
			};

			$ctrl.cancel = function() {
				$ctrl.removeInstance = false;
				$uibModalInstance.dismiss('cancel');
			};

			$ctrl.uploadFiles = function() {
				var modalInstance = shStaticFileUploadFactory.modalUploadFiles($ctrl.shObject.shFolder.id);
				modalInstance.result.then(function(shPostSelected) {
					// Selected Replace and Remove it
					$http.post(
							shAPIServerService.get().concat(
									"/v2/reference/to/" + shObject.id + "/replace/" + shPostSelected.id))
							.then(function(response) {
								$ctrl.objectRefers = response.data;
							});
					
				}, function() {
					// Selected CANCEL
				});
			}
		} ]);

shioApp.controller('ShStaticFileUploadSelectCtrl', [
	'$scope',
	'shAPIServerService',
	'$http',
	'$uibModalInstance',
	'shWidgetFileFactory',
	'Upload',
	'$timeout',
	'folderId',
	function ($scope, shAPIServerService, $http, $uibModalInstance, shWidgetFileFactory, Upload, $timeout, folderId) {
		var $ctrl = this;
		$scope.closeButtonLabel = "Cancel";
		$scope.shFiles = null;
		$scope.hasFiles = false;
		$scope.startUpload = false;
		$scope.uploadCompleted = false;
		$scope.$watch('shFiles', function () {
			if ($scope.shFiles != null) {
				$ctrl.listFiles($scope.shFiles, null);
			}
		});
		$ctrl.shPostSelected = null;
		$ctrl.ok = function () {
			$uibModalInstance.close($ctrl.shPostSelected);
		};

		$ctrl.cancel = function () {
			if ($scope.startUpload) {
				$uibModalInstance.close("finished");
			}
			else {
				$uibModalInstance.dismiss('cancel');
			}
		};
		$scope.shSite = null;
		$scope.shFolders = null;
		$scope.shPosts = null;
		$scope.breadcrumb = null;
		$scope.currentFolder = null;

		// BEGIN Functions
		$ctrl.listFiles = function (files, errFiles) {
			$scope.hasFiles = true;
			$scope.files = files;
			$scope.files.forEach(function (file) {
				file.error = null;
				$http.get(shAPIServerService.get().concat('/v2/staticfile/pre-upload/' + folderId + '/' + file.name))
					.then(function successCallback(response) {
						//
					}, function errorCallback(response) {
						file.error = response.data.title + ". " + response.data.message;					
					})
			});
		}
		$ctrl.uploadFiles = function (files, errFiles) {

			$scope.startUpload = true;
			$scope.closeButtonLabel = "Close";

			//$scope.files = files;
			angular.forEach($scope.files, function (file) {
				$scope.f = file;
				$scope.errFile = errFiles && errFiles[0];

				if (file && file.error == null) {
					file.upload = Upload.upload({
						url: shAPIServerService.get().concat('/v2/staticfile/upload'),
						data: {
							file: file,
							folderId: folderId,
							createPost: true
						}
					});
					file.upload.then(function (response) {
						$timeout(function () {
							//		$scope.shPosts.unshift(response.data);
							//		file.result = response.data;
						});
					}, function (response) {
						if (response.status > 0)
							file.error = response.data.title + ". " + response.data.message;
					}, function (evt) {
						file.progress = Math.min(100, parseInt(100.0 *
							evt.loaded / evt.total));
					});
				}
			});
		}
		// END Functions

	}]);

shioApp.controller('ShWidgetCheckBoxCtrl', [
	'$scope',
	function ($scope) {
		$scope.widgetSettingsObject = null;
		$scope.choices = [];
		$scope.checkItems = [];
		$scope.init = function (widgetSettings, arrayValue) {
			$scope.widgetSettingsObject = angular.fromJson(widgetSettings);
			var choicesArray = $scope.widgetSettingsObject.choices.replace(/(\r\n\n\r)/gm, "\n").split("\n");
			angular.forEach(choicesArray, function (value, key) {
				var choiceRowArray = value.split(":");
				var choiceRow = {
					id :choiceRowArray[0],
					label : choiceRowArray[1],
					checked: (arrayValue.indexOf(choiceRowArray[0]) > -1)
				}
				
				if (arrayValue.indexOf(choiceRowArray[0]) > -1)
					$scope.checkItems.push(choiceRowArray[0]);
				
				$scope.choices.push(choiceRow);

			});
		}

		$scope.updateCheckBox = function (item) {
			if (item.checked) {
				$scope.checkItems.push(item.id);
			}
			else {
				var index = $scope.checkItems.indexOf(item.id);
				$scope.checkItems.splice(index, 1);
			}
			return $scope.checkItems;

		}
	}]);

shioApp.controller('ShWidgetComboBoxCtrl', [
	'$scope',
	'Upload',
	'$timeout',
	'$uibModal',
	'shWidgetRelatorFactory',
	'$filter',
	function ($scope, Upload, $timeout, $uibModal, shWidgetRelatorFactory,
		$filter) {
		$scope.widgetSettingsObject = null;
		$scope.choices = [];

		$scope.init = function (widgetSettings) {
			$scope.widgetSettingsObject = angular.fromJson(widgetSettings);
			var choicesArray = $scope.widgetSettingsObject.choices.replace(/(\r\n\n\r)/gm, "\n").split("\n");
			angular.forEach(choicesArray, function (value, key) {
				var choiceRowArray = value.split(":");
				$scope.choices.push({
					id: choiceRowArray[0],
					label: choiceRowArray[1]
				});

			});
		}
	}]);

shioApp.factory('shWidgetContentSelectFactory', ['$uibModal',
	function ($uibModal) {
		const varToString = varObj => Object.keys(varObj)[0];
		return {
			modalSelectFile: function (shFolder) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true,
					ariaLabelledBy: 'modal-title',
					ariaDescribedBy: 'modal-body',
					templateUrl: 'template/widget/content-select/content-select-select.html',
					controller: 'ShWidgetContentSelectSelectCtrl',
					controllerAs: varToString({ $ctrl }),
					size: null,
					appendTo: undefined,
					resolve: {
						shFolder: function () {
							return shFolder;
						}
					}
				});
			}
		}
	}]);

shioApp
		.controller(
				'ShWidgetContentSelectCtrl',
				[
						'$rootScope',
						'$scope',
						'Upload',
						'$timeout',
						'$uibModal',
						'ShDialogSelectObject',
						function($rootScope, $scope, Upload, $timeout,
								$uibModal, ShDialogSelectObject) {
							$scope.breadcrumbCS = [];
							$scope.init = function(shPostAttr) {
								$scope.updateCSInfo(shPostAttr);
							}
							$scope.folderBreadcrumb = function(breadcrumb,
									shFolder) {
								if (shFolder != null) {
									var breadcrumbItem = {};
									breadcrumbItem.id = shFolder.id;
									breadcrumbItem.name = shFolder.name;
									breadcrumb.push(breadcrumbItem);
									if (shFolder.parentFolder != null) {
										$scope.folderBreadcrumb(breadcrumb,
												shFolder.parentFolder);
									} else if (shFolder.shSite != null) {
										breadcrumbItem = {};
										breadcrumbItem.id = shFolder.shSite.id;
										breadcrumbItem.name = shFolder.shSite.name
												+ " (Site)";
										breadcrumb.push(breadcrumbItem);
									}
								}
							}
							$scope
									.$watch(
											'shPostAttr.file',
											function() {
												if ($scope.shPostAttr.file != null) {
													$scope.uploadNewFile = false;
													$scope.shPostAttr.strValue = $scope.fileName;
												}
											});

							$scope.updateCSInfo = function(shPostAttr) {
								$scope.breadcrumbCS = [];

								if ((typeof shPostAttr.referenceObject != 'undefined')
										&& (shPostAttr.referenceObject != null)) {
									if (shPostAttr.referenceObject.objectType == "FOLDER") {
										$scope
												.folderBreadcrumb(
														$scope.breadcrumbCS,
														shPostAttr.referenceObject.parentFolder);
									} else if (shPostAttr.referenceObject.objectType == "POST") {
										$scope
												.folderBreadcrumb(
														$scope.breadcrumbCS,
														shPostAttr.referenceObject.shFolder);
									}
								}

							}

							$scope.clearContent = function(shPostAttr) {
								shPostAttr.strValue = null;
								shPostAttr.file = null;
								$scope.breadcrumbCS = [];
							}

							$scope.selectContent = function(shPost, shPostAttr) {

								var widgetSettingsObject = angular
										.fromJson(shPostAttr.shPostTypeAttr.widgetSettings);
								var folderIdSetting = null;
								if (widgetSettingsObject != null) {
									folderIdSetting = widgetSettingsObject.contentSelect.folderId;
								}
								var shFolderId = null;
								if (folderIdSetting != null
										&& folderIdSetting.trim().length > 0) {
									shFolderId = folderIdSetting;
								} else {
									shFolderId = $rootScope.shFolder.id;
								}
								var modalInstance = ShDialogSelectObject
										.dialog(shFolderId, "shObject");
								modalInstance.result
										.then(
												function(shObjectSelected) {
													// Selected INSERT
													shPostAttr.strValue = shObjectSelected.id;
													shPostAttr.referenceObject = shObjectSelected;
													$scope
															.updateCSInfo(shPostAttr);
												}, function() {
													// Selected CANCEL
												});

							}

							$scope.selectContentByObject = function(shPostAttr,
									shObject) {
								var modalInstance = ShDialogSelectObject
										.dialog(shObject.id, "shObject");
								modalInstance.result
										.then(
												function(shObjectSelected) {
													// Selected INSERT
													shPostAttr.strValue = shObjectSelected.id;
													shPostAttr.referenceObject = shObjectSelected;
													$scope
															.updateCSInfo(shPostAttr);
												}, function() {
													// Selected CANCEL
												});
							}
						} ]);

shioApp.controller('ShWidgetContentSelectSelectCtrl', [
		'$scope',
		'shAPIServerService',
		'$http',
		'$uibModalInstance',
		'shFolder',
		'shWidgetContentSelectFactory',
		function($scope, shAPIServerService, $http, $uibModalInstance,
				shFolder,shWidgetContentSelectFactory) {
			var $ctrl = this;

			$ctrl.shObjectSelected = null;
			$ctrl.ok = function() {
				$uibModalInstance.close($ctrl.shObjectSelected);
			};

			$ctrl.cancel = function() {
				$ctrl.shObjectSelected = null;
				$uibModalInstance.dismiss('cancel');
			};
			$scope.shSite = null;
			$scope.shFolders = null;
			$scope.shPosts = null;
			$scope.breadcrumb = null;

			// BEGIN Functions
			$scope.folderList = function(shFolderItem) {
				$ctrl.shObjectSelected = null;
				$scope.$evalAsync($http.get(
						shAPIServerService.get().concat(
								"/v2/object/" + shFolderItem.id + "/list"))
						.then(function(response) {
							$scope.shFolders = response.data.shFolders;
							$scope.shPosts = response.data.shPosts;
							$scope.breadcrumb = response.data.breadcrumb;
							$scope.shSite = response.data.shSite;
						}));
			}

			$scope.siteList = function(siteId) {
				$ctrl.shObjectSelected = null;
				$scope.$evalAsync($http.get(
						shAPIServerService.get().concat(
								"/v2/site/" + siteId + "/folder")).then(
						function(response) {
							$scope.shFolders = response.data.shFolders;
							$scope.shPosts = response.data.shPosts;
							$scope.shSite = response.data.shSite;
						}));
			}

			$scope.selectedObject = function(shObject) {
				$ctrl.shObjectSelected = shObject;
			}
			
			// END Functions

			$scope.folderList(shFolder);

		} ]);

shioApp
	.controller(
		'ShWidgetMultiSelectSettingCtrl',
		[
			'$scope',
			'$http',
			'shPostTypeResource',
			function ($scope, $http,shPostTypeResource) {
				$scope.postTypes = shPostTypeResource.query();
				
			}
		]);
shioApp
	.controller(
		'ShWidgetDateCtrl',
		['$scope',
			function ($scope) {
				$scope.currentDate = function () {
					if ($scope.shPostAttr !== null && $scope.shPostAttr.dateValue !== null) {
						var dateAttr = $scope.shPostAttr.dateValue.split('T');
						dateAttr = dateAttr[0].split('-');
						$scope.shPostAttr.dateValue = new Date(dateAttr[0], dateAttr[1] - 1, dateAttr[2]);
					}
					else {
						$scope.shPostAttr.dateValue = null;
					}
				};
				$scope.currentDate();

				$scope.clear = function () {
					$scope.shPostAttr.dateValue = null;
				};

				$scope.inlineOptions = {
					customClass: getDayClass,
					showWeeks: true
				};

				$scope.dateOptions = {
					formatYear: 'yy',
					startingDay: 1
				};
				$scope.open1 = function () {
					$scope.popup1.opened = true;
				};

				$scope.open2 = function () {
					$scope.popup2.opened = true;
				};

				$scope.setDate = function (year, month, day) {
					$scope.shPostAttr.dateValue = new Date(year, month, day);
				};

				$scope.formats = ['dd-MMMM-yyyy', 'yyyy/MM/dd',
					'dd/MM/yyyy', 'shortDate'];
				$scope.format = $scope.formats[2];
				$scope.altInputFormats = ['M!/d!/yyyy'];

				$scope.popup1 = {
					opened: false
				};

				$scope.popup2 = {
					opened: false
				};

				function getDayClass(data) {
					var date = data.date, mode = data.mode;
					if (mode === 'day') {
						var dayToCheck = new Date(date).setHours(0,
							0, 0, 0);

						for (var i = 0; i < $scope.events.length; i++) {
							var currentDay = new Date(
								$scope.events[i].date)
								.setHours(0, 0, 0, 0);

							if (dayToCheck === currentDay) {
								return $scope.events[i].status;
							}
						}
					}

					return '';
				}

			}]);

shioApp.factory('shWidgetResource', [ '$resource', 'shAPIServerService', function($resource, shAPIServerService) {
	return $resource(shAPIServerService.get().concat('/v2/widget/:id'), {
		id : '@id'
	}, {
		update : {
			method : 'PUT'
		}
	});
} ]);

shioApp.factory('shWidgetFileFactory', ['$uibModal',
	function ($uibModal) {
		const varToString = varObj => Object.keys(varObj)[0];
		return {
			modalSelectFile: function (shFolder) {
				var $ctrl = this;
				return $uibModal.open({
					animation: true,
					ariaLabelledBy: 'modal-title',
					ariaDescribedBy: 'modal-body',
					templateUrl: 'template/widget/file/file-select.html',
					controller: 'ShWidgetFileSelectCtrl',
					controllerAs: varToString({ $ctrl }),
					size: 'lg',
					appendTo: undefined,
					resolve: {
						shFolder: function () {
							return shFolder;
						}
					}
				});
			}
		}
	}]);

shioApp.controller('ShWidgetFileCtrl', [
		'$scope',		
		'shWidgetFileFactory',
		'shAPIServerService',
		'shFolderResource',
		function($scope, shWidgetFileFactory,
				shAPIServerService, shFolderResource) {
			$scope.fileName = null;
			$scope.uploadNewFile = false;
			$scope.$watch('shPostAttr.file', function() {
				if ($scope.shPostAttr.file != null) {
					$scope.uploadNewFile = false;
					$scope.fileName = $scope.shPostAttr.file.name;
					$scope.shPostAttr.strValue = $scope.fileName;					
				}
			});
			$scope.imageURL = null;
			$scope.breadcrumbFile = [];
			$scope.shPostFile = null;
			$scope.init = function(shPost, shPostAttr) {
				$scope.shPostFile = shPost;
				$scope.updateFileInfo(shPostAttr);
			}

			$scope.updateFileInfo = function(shPostAttr) {
				$scope.breadcrumbFile = [];
				if ((typeof shPostAttr.referenceObject != 'undefined')
						&& (shPostAttr.referenceObject != null)) {
					$scope.folderBreadcrumb($scope.breadcrumbFile,
							shPostAttr.referenceObject.shFolder);
					$scope.getImageURL(shPostAttr.referenceObject);
				} else {
					$scope.getImageURL($scope.shPostFile);
				}

			}
			$scope.folderBreadcrumb = function(breadcrumb, shFolder) {
				if (shFolder != null) {
					var breadcrumbItem = {};
					breadcrumbItem.id = shFolder.id;
					breadcrumbItem.name = shFolder.name;
					breadcrumb.push(breadcrumbItem);
					if (shFolder.parentFolder != null) {
						$scope.folderBreadcrumb(breadcrumb,
								shFolder.parentFolder);
					} else if (shFolder.shSite != null) {
						breadcrumbItem = {};
						breadcrumbItem.id = shFolder.shSite.id;
						breadcrumbItem.name = shFolder.shSite.name + " (Site)";
						breadcrumb.push(breadcrumbItem);
					}
				}
			}

			$scope.newFile = function() {
				$scope.uploadNewFile = true;
			}
			$scope.clearFile = function(shPostAttr) {
				shPostAttr.strValue = null;
				shPostAttr.file = null;
				shPostAttr.referenceObject = null;
				$scope.breadcrumbFile = [];
				$scope.imageURL = null;
			}

			$scope.selectFile = function(shPost, shPostAttr) {
				var widgetSettingsObject = angular
						.fromJson(shPostAttr.shPostTypeAttr.widgetSettings);
				var folderIdSetting = null;
				if (widgetSettingsObject != null) {
					folderIdSetting = widgetSettingsObject.file.folderId;
				}
				if (folderIdSetting != null
						&& folderIdSetting.trim().length > 0) {

					var shFolderSetting = shFolderResource.get({
						id : widgetSettingsObject.file.folderId
					}, function() {
						$scope.modalExplorer(shFolderSetting, shPostAttr);

					});
				} else {
					$scope.modalExplorer($scope.shFolder, shPostAttr);
				}

			}

			$scope.modalExplorer = function(shFolder, shPostAttr) {
				var modalInstance = shWidgetFileFactory
						.modalSelectFile(shFolder);
				modalInstance.result.then(function(shPostSelected) {
					// Selected INSERT
					$scope.uploadNewFile = false;
					$scope.fileName = shPostSelected.title;
					shPostAttr.strValue = shPostSelected.id;
					shPostAttr.referenceObject = shPostSelected;
					$scope.updateFileInfo(shPostAttr);
				}, function() {
					// Selected CANCEL
				});
			}

			$scope.selectFileByObject = function(shPostAttr, shObject) {
				var modalInstance = shWidgetFileFactory
						.modalSelectFile(shObject);
				modalInstance.result.then(function(shPostSelected) {
					// Selected INSERT
					$scope.uploadNewFile = false;
					$scope.fileName = shPostSelected.title;
					shPostAttr.strValue = shPostSelected.id;
					shPostAttr.referenceObject = shPostSelected;
					$scope.updateFileInfo(shPostAttr);
				}, function() {
					// Selected CANCEL
				});
			}

			$scope.getImageURL = function(shPost) {
				$scope.imageURL = null;
				if (shPost != null && shPost.title != null && shPost.title.match(/.(jpg|jpeg|png|gif)$/i)) {
					$scope.imageURL = shAPIServerService.get().concat(
							"/v2/object/" + shPost.id + "/preview");
				}
			}

		} ]);

shioApp.controller('ShWidgetFileSelectCtrl', [
		'$scope',
		'shAPIServerService',
		'$http',
		'$uibModalInstance',
		'shFolder',
		'shWidgetFileFactory',
		'Upload',
		'$timeout',
		function($scope, shAPIServerService, $http, $uibModalInstance,
				shFolder,shWidgetFileFactory, Upload, $timeout) {
			var $ctrl = this;

			$ctrl.shPostSelected = null;
			$ctrl.ok = function() {
				$uibModalInstance.close($ctrl.shPostSelected);
			};

			$ctrl.cancel = function() {
				$ctrl.shPostSelected = null;
				$uibModalInstance.dismiss('cancel');
			};
			$scope.shSite = null;
			$scope.shFolders = null;
			$scope.shPosts = null;
			$scope.breadcrumb = null;
			$scope.currentFolder = null;
			$ctrl.orderAsc = true;
			$ctrl.orderBy = 'name';
			$ctrl.orderByPostSelected = "title";
        	$ctrl.orderByFolderSelected = "name";
			$ctrl.shSearchFilter = "";
			
			// BEGIN Functions

			$ctrl.orderList = function () {
				if ($ctrl.orderAsc) {
					$ctrl.orderAsc = false;
				}
				else {
					$ctrl.orderAsc = true;
				}
				$ctrl.orderBySelection();
			}

			$ctrl.orderBySelection = function () {
				var orderPrefix = "";
				if (!$ctrl.orderAsc) {
					orderPrefix = "-";
				}
				
				if ($ctrl.orderBy === 'name') {
					$ctrl.orderByPostSelected = orderPrefix + "title";
					$ctrl.orderByFolderSelected = orderPrefix + "name";            
				}
				if ($ctrl.orderBy === 'date') {
					$ctrl.orderByPostSelected = orderPrefix + "date";
					$ctrl.orderByFolderSelected = orderPrefix + "date";
				}
			}

			$scope.showSearchFolderAction = function () {
				$scope.searchFolder = true;
				focus('search-folder-input');			
			}

			$scope.hideSearchFolderAction = function () {
				$scope.searchFolder = false;
				$ctrl.shSearchFilter = "";
			}

			$scope.folderList = function(shFolderItem) {
				$scope.currentFolder = shFolderItem;
				$ctrl.shPostSelected = null;
				$scope.$evalAsync($http.get(
						shAPIServerService.get().concat(
								"/v2/object/" + shFolderItem.id + "/list/File"))
						.then(function(response) {
							$scope.shFolders = response.data.shFolders;
							$scope.shPosts = response.data.shPosts;
							$scope.breadcrumb = response.data.breadcrumb;
							$scope.shSite = response.data.shSite;
						}));
			}

			$scope.siteList = function(siteId) {
				$ctrl.shPostSelected = null;
				$scope.$evalAsync($http.get(
						shAPIServerService.get().concat(
								"/v2/site/" + siteId + "/folder")).then(
						function(response) {
							$scope.shFolders = response.data.shFolders;
							$scope.shPosts = response.data.shPosts;
							$scope.shSite = response.data.shSite;
							$scope.breadcrumb = null;
						}));
			}

			$scope.selectedPost = function(shPost) {
				$ctrl.shPostSelected = shPost;
			}

			$scope.uploadFile = function(file, errFiles) {
				$scope.f = file;
				$scope.errFile = errFiles && errFiles[0];
				if (file) {
					file.upload = Upload.upload({
						url: shAPIServerService.get().concat('/v2/staticfile/upload'),
						data: {
							file: file,
							folderId:  $scope.currentFolder.id,
							createPost: true
						}
					});					
					file.upload.then(function (response) {
						$timeout(function () {						
							$scope.shPosts.unshift(response.data);
							file.result = response.data;
						});
					}, function (response) {
						if (response.status > 0)
							$scope.errorMsg = response.status + ': ' + response.data;
					}, function (evt) {
						file.progress = Math.min(100, parseInt(100.0 * 
												 evt.loaded / evt.total));
					});
				}   
			}			
			// END Functions

			$scope.folderList(shFolder);

		} ]);

shioApp
	.controller(
		'ShWidgetFileSettingCtrl',
		[
			'$scope',
			'$http',
			'shPostTypeResource',
			function ($scope, $http,shPostTypeResource) {
				$scope.postTypes = shPostTypeResource.query();
				
			}
		]);
shioApp
		.controller(
				'ShWidgetFormConfigurationCtrl',
				[
						'$scope',
						'$http',
						'$q',
						'shAPIServerService',
						'shPostTypeResource',
						function($scope, $http, $q, shAPIServerService,
								shPostTypeResource) {
							$scope.pageLayouts = null;
							$scope.pageLayoutMap = [];
							$scope.shPostTypeAttrForm = null;
							$scope.init = function(shPostTypeAttr) {
								$scope.shPostTypeAttrForm = shPostTypeAttr;
								/*if ($scope.shPostTypeAttrForm.widgetSettingsObject === undefined
										|| $scope.shPostTypeAttrForm.widgetSettingsObject === null) {
									$scope.shPostTypeAttrForm.widgetSettingsObject = [];
								}							
								if ($scope.shPostTypeAttrForm.widgetSettingsObject.method === undefined) {
									$scope.shPostTypeAttrForm.widgetSettingsObject.method = "POST";
								}*/

							}

							$scope
									.$evalAsync($http
											.get(
													shAPIServerService
															.get()
															.concat(
																	"/v2/post/post-type/PageLayout"))
											.then(
													function(response) {
														$scope.pageLayouts = response.data;
														angular
																.forEach(
																		$scope.paymentTypes,
																		function(
																				value,
																				key) {
																			$scope.pageLayoutMap[value.id] = value;
																		});
													}));
						} ]);

shioApp.controller('ShWidgetMultiSelectCtrl', [
		'$scope',
		'$http',
		'$timeout',
		'$interval',
		'shPostTypeResource',
		'shAPIServerService',
		function($scope, $http, $timeout, $interval, shPostTypeResource, shAPIServerService) {
			var vm = this;
			$scope.widgetSettingsObject = null;
			$scope.choices = [];
			vm.postType = null;
			vm.folderId = null;
			vm.fieldValue = null;
			vm.fieldName = null;
			vm.posts = [];
			vm.init = function(widgetSettings) {
				$scope.widgetSettingsObject = angular.fromJson(widgetSettings);
				vm.postType = $scope.widgetSettingsObject.multiselect.postType;
				vm.folderId = $scope.widgetSettingsObject.multiselect.folderId;
				vm.fieldValue = $scope.widgetSettingsObject.multiselect.value;
				vm.fieldName = $scope.widgetSettingsObject.multiselect.name;

				$scope.$evalAsync($http.get(
						shAPIServerService.get().concat(
								"/v2/object/" + vm.folderId
										+ "/list/" + vm.postType)).then(
						function(response) {
							vm.posts = response.data.shPosts;
						}));
			}

			vm.msdisabled = false;

			vm.selectedPosts = [];
		} ]);
shioApp
	.controller(
		'ShWidgetContentSelectSettingCtrl',
		[
			'$scope',
			'$http',
			'shPostTypeResource',
			function ($scope, $http,shPostTypeResource) {
				$scope.postTypes = shPostTypeResource.query();
				
			}
		]);
shioApp.factory('shWidgetRelatorFactory', [ '$uibModal',
		function($uibModal) {
			const varToString = varObj => Object.keys(varObj)[0];
			return {
				modalSelectRelatorItem : function(shPostAttr, shChildrenRelatorItem) {
					var $ctrl = this;
					return $uibModal.open({
						animation : true,
						ariaLabelledBy : 'modal-title',
						ariaDescribedBy : 'modal-body',
						templateUrl : 'template/widget/relator/relator-select.html',
						controller : 'ShWidgetRelatorSelectCtrl',
						controllerAs : varToString({ $ctrl }),
						size : null,
						appendTo : undefined,
						resolve : {
							shChildrenRelatorItem : function() {
								return shChildrenRelatorItem;
							},
							shPostAttrRelator : function() {
								return shPostAttr;
							}
						}
					});
				},
				modalAddRelatorItem : function(shPostAttr) {
					var $ctrl = this;
					return $uibModal.open({
						animation : true,
						ariaLabelledBy : 'modal-title',
						ariaDescribedBy : 'modal-body',
						templateUrl : 'template/widget/relator/relator-select.html',
						controller : 'ShWidgetRelatorAddCtrl',
						controllerAs : varToString({ $ctrl }),
						size : null,
						appendTo : undefined,
						resolve : {
							shPostAttr : function() {
								return shPostAttr;
							}
						}
					});
				}
			}
		} ]);

shioApp.controller('ShWidgetRelatorAddCtrl', [
	'$scope',
	'shAPIServerService',
	'$http',
	'$uibModalInstance',
	'shPostAttr',
	'orderByFilter',
	function ($scope, shAPIServerService, $http, $uibModalInstance,
		shPostAttr, orderByFilter) {

		$scope.tinymceOptions = {
			plugins: 'shTinyMCE link image code lists paste advlist anchor autolink autoresize charmap codesample directionality emoticons hr insertdatetime legacyoutput media nonbreaking noneditable pagebreak preview print searchreplace tabfocus table template textpattern toc visualblocks visualchars wordcount',
			relative_urls: false,
			toolbar: ' undo redo | bold italic | alignleft aligncenter alignright | code | numlist bullist a11ycheck anchor charmap codesample ltr rtl | link image media shMailTo shAddImage shAddContent | format emoticons  insertdatetime nonbreaking pagebreak paste | preview print searchreplace | table tabledelete | tableprops tablerowprops tablecellprops | tableinsertrowbefore tableinsertrowafter tabledeleterow | tableinsertcolbefore tableinsertcolafter tabledeletecol | template toc visualblocks visualchars wordcount',
			menubar: 'edit insert tools view format table',
			min_height: 500
		};

		$scope.shPostAttrs = [];
		$scope.$evalAsync($http.get(
			shAPIServerService.get().concat("/v2/post/attr/model"))
			.then(function (response) {
				$scope.shPostAttrModel = response.data;

				$scope.shPostTypeAttrs = shPostAttr.shPostTypeAttr.shPostTypeAttrs;

				angular.forEach($scope.shPostTypeAttrs, function (value, key) {
					var attrModel = angular.copy($scope.shPostAttrModel);
					attrModel.shPostTypeAttr = value;
					this.push(attrModel);
				}, $scope.shPostAttrs);
			}));

		var $ctrl = this;

		$ctrl.shPostSelected = null;

		$scope.shSite = null;
		$scope.shFolders = null;
		$scope.shPosts = null;
		$scope.breadcrumb = null;

		// BEGIN Functions
		$ctrl.ok = function () {
			$scope.titles = [];
			$scope.summaries = [];
			angular.forEach(orderByFilter($scope.shPostAttrs, 'shPostTypeAttr.ordinal', false), function (shPostAttrItem, key) {
				if (shPostAttrItem.shPostTypeAttr.isTitle == 1) {
					if (shPostAttrItem.dateValue !== null) {
						$scope.titles.push($ctrl.formatDate(shPostAttrItem.dateValue));
					}
					else {
						$scope.titles.push(shPostAttrItem.referenceObject != null ? shPostAttrItem.referenceObject.title : shPostAttrItem.strValue);
					}
				}
				if (shPostAttrItem.shPostTypeAttr.isSummary == 1) {
					if (shPostAttrItem.dateValue !== null) {
						$scope.summaries.push($ctrl.formatDate(shPostAttrItem.dateValue));
					}
					else {
						$scope.summaries.push(shPostAttrItem.referenceObject != null ? shPostAttrItem.referenceObject.summary : shPostAttrItem.strValue);
					}
				}

			});
			$scope.title = $scope.titles.join(", ");
			if ($scope.title === null || ($scope.title !== null && $scope.title.trim().length === 0)) {
				$scope.title = "Untitled";
			}
			$scope.summary = $scope.summaries.join(", ");
			$uibModalInstance.close({
				shPostAttrs: $scope.shPostAttrs,
				title: $scope.title,
				summary: $scope.summary
			});
		};

		$ctrl.cancel = function () {
			$ctrl.shPostSelected = null;
			$uibModalInstance.dismiss('cancel');
		};
		// END Functions

		$ctrl.formatDate = function (date) {
			var d = new Date(date),
				month = '' + (d.getMonth() + 1),
				day = '' + d.getDate(),
				year = d.getFullYear();

			if (month.length < 2) month = '0' + month;
			if (day.length < 2) day = '0' + day;

			return [day, month, year].join('/');
		}
	}
]);
shioApp.controller('ShWidgetRelatorCtrl', [
		'$scope',
		'Upload',
		'$timeout',
		'$uibModal',
		'shWidgetRelatorFactory',
		'$filter',
		function($scope, Upload, $timeout, $uibModal, shWidgetRelatorFactory, $filter) {
			$scope.shPostAttr.shChildrenRelatorItems = $filter('orderBy')($scope.shPostAttr.shChildrenRelatorItems, 'ordinal');
			$scope.fileName = null;
			$scope.uploadNewFile = false;
			$scope.relatorItems = null;
			 
			$scope.newFile = function() {
				$scope.uploadNewFile = true;
			}
			$scope.clearFile = function(shPostAttr) {
				shPostAttr.strValue = null;
				shPostAttr.file = null;
			}
			
			$scope.relatorDelete = function(shPostAttr,index) {				 
                  shPostAttr.shChildrenRelatorItems.splice(index, 1);                  
			}
			
			$scope.addRelatorItem = function(shPost, shPostAttr) {
				var modalInstance = shWidgetRelatorFactory
						.modalAddRelatorItem(shPostAttr);
				modalInstance.result.then(function(transferData) {			
					// Selected SAVE
					if ($scope.shPostAttr.shChildrenRelatorItems == null) {
						$scope.shPostAttr.shChildrenRelatorItems = [];

					}
					
					var shChildRelatorItem = {};
					shChildRelatorItem["@type"] = "ShRelatorItem";
					shChildRelatorItem.id = null;
					shChildRelatorItem.title = transferData.title;
					shChildRelatorItem.summary = transferData.summary;
					shChildRelatorItem.shChildrenPostAttrs = transferData.shPostAttrs;
					
					$scope.shPostAttr.shChildrenRelatorItems.push(shChildRelatorItem);

				}, function() {
					// Selected CANCEL
				});
			}

			$scope.selectRelatorItem = function(shPostAttr, shChildRelatorItem) {
				var modalInstance = shWidgetRelatorFactory
						.modalSelectRelatorItem(shPostAttr, shChildRelatorItem);
				modalInstance.result.then(function(transferData) {
					// Selected SAVE
					shChildRelatorItem.title = transferData.title;
					shChildRelatorItem.summary = transferData.summary;
				}, function() {
					// Selected CANCEL
				});
			}
		} ]);

shioApp.controller('ShWidgetRelatorSelectCtrl', [
	'$scope',
	'shAPIServerService',
	'$http',
	'$uibModalInstance',
	'shPostAttrRelator',
	'shChildrenRelatorItem',
	'shWidgetFileFactory',
	'orderByFilter',
	function ($scope, shAPIServerService, $http, $uibModalInstance,
		shPostAttrRelator, shChildrenRelatorItem, shWidgetFileFactory, orderByFilter) {

		$scope.tinymceOptions = {
			plugins: 'shTinyMCE link image code lists paste advlist anchor autolink autoresize charmap codesample directionality emoticons hr insertdatetime legacyoutput media nonbreaking noneditable pagebreak preview print searchreplace tabfocus table template textpattern toc visualblocks visualchars wordcount',
			relative_urls: false,
			toolbar: ' undo redo | bold italic | alignleft aligncenter alignright | code | numlist bullist a11ycheck anchor charmap codesample ltr rtl | link image media shMailTo shAddImage shAddContent | format emoticons  insertdatetime nonbreaking pagebreak paste | preview print searchreplace | table tabledelete | tableprops tablerowprops tablecellprops | tableinsertrowbefore tableinsertrowafter tabledeleterow | tableinsertcolbefore tableinsertcolafter tabledeletecol | template toc visualblocks visualchars wordcount',
			menubar: 'edit insert tools view format table',
			min_height: 500
		};

		var $ctrl = this;
		$scope.shPostAttrs = shChildrenRelatorItem.shChildrenPostAttrs;
		$ctrl.shPostSelected = null;

		$scope.shSite = null;
		$scope.shFolders = null;
		$scope.shPosts = null;
		$scope.breadcrumb = null;

		$scope.widgetSettingsObject = angular.fromJson(shPostAttrRelator.shPostTypeAttr.widgetSettings);
		$scope.relatorTitle = $scope.widgetSettingsObject.relator.title;

		// BEGIN Functions
		$ctrl.ok = function () {
			$scope.titles = [];
			$scope.summaries = [];
			angular.forEach(orderByFilter($scope.shPostAttrs, 'shPostTypeAttr.ordinal', false), function (shPostAttr, key) {
				if (shPostAttr.shPostTypeAttr.isTitle == 1) {
					if (shPostAttr.dateValue !== null) {
						$scope.titles.push($ctrl.formatDate(shPostAttr.dateValue));
					}
					else {
						$scope.titles.push(shPostAttr.strValue);
					}
				}
				if (shPostAttr.shPostTypeAttr.isSummary == 1) {
					if (shPostAttr.dateValue !== null) {
						$scope.summaries.push($ctrl.formatDate(shPostAttr.dateValue));
					}
					else {
						$scope.summaries.push(shPostAttr.strValue);
					}

				}
			});

			$scope.title = $scope.titles.join(", ");
			$scope.summary = $scope.summaries.join(", ");
			$uibModalInstance.close({ shPostAttrs: $scope.shPostAttrs, title: $scope.title, summary: $scope.summary });
		};

		$ctrl.cancel = function () {
			$ctrl.shPostSelected = null;
			$uibModalInstance.dismiss('cancel');
		};

		$scope.selectedPost = function (shPost) {
			$ctrl.shPostSelected = shPost;
		}

		$ctrl.formatDate = function (date) {
			var d = new Date(date),
				month = '' + (d.getMonth() + 1),
				day = '' + d.getDate(),
				year = d.getFullYear();

			if (month.length < 2) month = '0' + month;
			if (day.length < 2) day = '0' + day;

			return [day, month, year].join('/');
		}
		// END Functions
	}]);

shioApp.controller('ShWidgetRelatorSettingCtrl', [
		'$scope',
		'$filter',
		function($scope, $filter) {
			$scope.shPostTypeAttr.shPostTypeAttrs = $filter('orderBy')(					
					$scope.shPostTypeAttr.shPostTypeAttrs, 'ordinal');

		} ]);
shioApp.controller('ShWidgetTinyMCECtrl', [
	'$scope',
	'shWidgetFileFactory',
	'shAPIServerService',
	'shFolderResource',
	'ShDialogSelectObject',
	'$q',
	'$http',
	function ($scope, shWidgetFileFactory,
		shAPIServerService, shFolderResource, ShDialogSelectObject, $q, $http) {

		$scope.fileName = null;
		$scope.uploadNewFile = false;
		$scope.$watch('shPostAttr.file', function () {
			if ($scope.shPostAttr.file != null) {
				$scope.uploadNewFile = false;
				$scope.fileName = $scope.shPostAttr.file.name;
				$scope.shPostAttr.strValue = $scope.fileName;
			}
		});
		$scope.imageURL = null;
		$scope.breadcrumbFile = [];
		$scope.shPostFile = null;
		$scope.init = function (shPost, shPostAttr) {
			$scope.shPostFile = shPost;
			$scope.updateFileInfo(shPostAttr);
		}

		$scope.updateFileInfo = function (shPostAttr) {
			$scope.breadcrumbFile = [];
			if ((typeof shPostAttr.referenceObject != 'undefined')
				&& (shPostAttr.referenceObject != null)) {
				$scope.folderBreadcrumb($scope.breadcrumbFile,
					shPostAttr.referenceObject.shFolder);
				$scope.getImageURL(shPostAttr.referenceObject);
			} else {
				$scope.getImageURL($scope.shPostFile);
			}

		}
		$scope.folderBreadcrumb = function (breadcrumb, shFolder) {
			if (shFolder != null) {
				var breadcrumbItem = {};
				breadcrumbItem.id = shFolder.id;
				breadcrumbItem.name = shFolder.name;
				breadcrumb.push(breadcrumbItem);
				if (shFolder.parentFolder != null) {
					$scope.folderBreadcrumb(breadcrumb,
						shFolder.parentFolder);
				} else if (shFolder.shSite != null) {
					breadcrumbItem = {};
					breadcrumbItem.id = shFolder.shSite.id;
					breadcrumbItem.name = shFolder.shSite.name + " (Site)";
					breadcrumb.push(breadcrumbItem);
				}
			}
		}

		$scope.newFile = function () {
			$scope.uploadNewFile = true;
		}
		$scope.clearFile = function (shPostAttr) {
			shPostAttr.strValue = null;
			shPostAttr.file = null;
			shPostAttr.referenceObject = null;
			$scope.breadcrumbFile = [];
			$scope.imageURL = null;
		}

		$scope.modalExplorer = function () {
			var deferred = $q.defer();
			var modalInstance = shWidgetFileFactory
				.modalSelectFile($scope.shFolder);
			modalInstance.result.then(function (shPostSelected) {
				$http.get(shAPIServerService.get().concat('/v2/object/' + shPostSelected.id + '/url')
				).then(function (response) {
					var object = {
						"url": response.data.url,
						"label": response.data.label
					}
					deferred.resolve(object);
				});
			}, function () {
				deferred.resolve(null);
				// Selected CANCEL
			});
			return deferred.promise;
		}
		$scope.selectContent = function () {
			var deferred = $q.defer();
			var modalInstance = ShDialogSelectObject
				.dialog($scope.shFolder.id, "shObject");
			modalInstance.result
				.then(
					function (shObjectSelected) {
						$http.get(shAPIServerService.get().concat('/v2/object/' + shObjectSelected.id + '/url')
						).then(function (response) {
							var object = {
								"url": response.data.url,
								"label": response.data.label
							}
							deferred.resolve(object);
						});
					}, function () {
						deferred.resolve(null);
						// Selected CANCEL
					});
			return deferred.promise;
		}
		$scope.selectFileByObject = function (shPostAttr, shObject) {
			var modalInstance = shWidgetFileFactory
				.modalSelectFile(shObject);
			modalInstance.result.then(function (shPostSelected) {
				// Selected INSERT
				$scope.uploadNewFile = false;
				$scope.fileName = shPostSelected.title;
				shPostAttr.strValue = shPostSelected.id;
				shPostAttr.referenceObject = shPostSelected;
				$scope.updateFileInfo(shPostAttr);
			}, function () {
				// Selected CANCEL
			});
		}

		$scope.getImageURL = function (shPost) {
			$scope.imageURL = null;
			if (shPost != null && shPost.title != null && shPost.title.match(/.(jpg|jpeg|png|gif)$/i)) {
				$scope.imageURL = shAPIServerService.get().concat(
					"/v2/object/" + shPost.id + "/preview");
			}
		}

	}]);

