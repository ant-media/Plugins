{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  packages = with pkgs; [
    nodejs_24
    bun
  ];

  shellHook = ''
    echo "AMS MoQ Player/Publisher dev environment"
    echo "  npm install    pulls @moq/watch and @moq/publish from npm"
    echo "  npm run dev    start Vite dev server"
    echo "  npm run build  production build"
  '';
}
