{
  description = "Racer Pacer";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-22.05";
    flake-compat = {
      url = github:edolstra/flake-compat;
      flake = false;
    };
    flake-utils.url = "github:numtide/flake-utils";
    clj2nix = {
      url = "github:hlolli/clj2nix";
      inputs = {
        nixpkgs.follows = "nixpkgs";
        flake-compat.follows = "flake-compat";
        flake-utils.follows = "flake-utils";
      };
    };
  };

  outputs = { self, nixpkgs, flake-utils, flake-compat, clj2nix }:
    let
      version = "${nixpkgs.lib.substring 0 8 self.lastModifiedDate}.${self.shortRev or "dirty"}";
    in

    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};

        clj-deps = import ./deps.nix { inherit (pkgs) fetchMavenArtifact fetchgit lib; };
        classpath = clj-deps.makeClasspaths { };

        buildSite = pkgs.stdenv.mkDerivation {
          name = "racer-pacer-${version}";

          buildInputs = [ pkgs.clojure ];

          nodeModules = pkgs.mkYarnModules rec {
            pname = "racer-pacer";
            name = "racer-pacer-node-modules-${version}";
            inherit version;
            packageJSON = ./package.json;
            yarnLock = ./yarn.lock;
          };

          src = self;

          configurePhase = ''
            ln -s $nodeModules/node_modules .
          '';

          buildPhase = ''
            export HOME=$PWD
            clojure -Scp ${classpath}:src/main -M:shadow-cljs release app
          '';

          installPhase = ''
            mkdir $out
            cp -r public/* $out
          '';
        };

      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            clj2nix.defaultPackage.${system}
            clojure
            ghp-import
            yarn
          ];
        };

        defaultPackage = self.packages.${system}.site;

        packages = {
          site = buildSite;
        };
      });
}
