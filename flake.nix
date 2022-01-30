{
  description = "Racer Pacer";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-21.05";
    flake-utils.url = "github:numtide/flake-utils";
    clj2nix = {
      url = "github:hlolli/clj2nix";
      inputs = {
        nixpkgs.follows = "nixpkgs";
        flake-utils.follows = "flake-utils";
      };
    };
  };

  outputs = { self, nixpkgs, flake-utils, clj2nix }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};

        clj-deps = import ./deps.nix { inherit (pkgs) fetchMavenArtifact fetchgit lib; };
        classpath = clj-deps.makeClasspaths { };

        buildSite = pkgs.stdenv.mkDerivation {
          name = "pacer-thewagner-net-${self.shortRev or "dirty"}";

          buildInputs = [ pkgs.clojure ];

          src = builtins.path {
            path = ./.;
            name = "src";
          };

          buildPhase = false;

          installPhase = ''
            export HOME=$PWD

            mkdir -p $out/js

            clojure -Scp ${classpath}:src -M:prod

            cp build/js/racer_pacer.js $out/js
            cp resources/public/index.html $out
          '';
        };

      in
      {
        devShell = pkgs.mkShell {
          buildInputs = with pkgs; [
            clojure
            clj-kondo
            clj2nix.defaultPackage.${system}
          ];
        };

        defaultPackage = self.packages.${system}.site;

        packages = {
          site = buildSite;
        };
      });
}
