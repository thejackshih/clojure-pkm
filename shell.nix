let
  sources = import ./npins;
  pkgs = import sources.nixpkgs {};
in
pkgs.mkShell {
  packages = with pkgs; [
    openjdk
    clojure
    nodejs
    rlwrap
    neil
    clojure-lsp
  ];

  shellHook = ''
    echo "=== Clojure PKM 開發環境載入完成 ==="
    java -version
    clojure --version
    node -v
  '';
}
