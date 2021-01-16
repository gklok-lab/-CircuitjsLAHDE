/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.

    CircuitJS1 is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    CircuitJS1 is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with CircuitJS1.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.lushprojects.circuitjs1.client;

import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.event.dom.client.ClickHandler;

import java.util.HashMap;
import java.util.Vector;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.Label;

public class ImportFromSpiceDialog extends DialogBox {
	
VerticalPanel vp;
HorizontalPanel hp;
CirSim sim;
// RichTextArea textBox;
TextArea textArea;
TextArea outputArea;
	
	public ImportFromSpiceDialog( CirSim asim) {
		super();
		sim=asim;
		Button okButton, cancelButton;
		vp=new VerticalPanel();
		setWidget(vp);
		setText(sim.LS("Import Subcircuit from Spice"));
		vp.add(new Label(sim.LS("Paste the text file for your subcircuit here...")));
//		vp.add(textBox = new RichTextArea());
		vp.add(textArea = new TextArea());
		textArea.getElement().setAttribute("spellcheck", "false");
		textArea.setWidth("300px");
		textArea.setHeight("200px");
		vp.add(outputArea = new TextArea());
		outputArea.setWidth("300px");
		outputArea.setHeight("100px");
		hp = new HorizontalPanel();
		vp.add(hp);
		hp.add(okButton = new Button(sim.LS("OK")));
		okButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
			    parseCircuit();
			}
		});
		hp.add(cancelButton = new Button(sim.LS("Cancel")));
		cancelButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				closeDialog();
			}
		});
		this.center();
		show();
	}
	
	protected void closeDialog()
	{
		this.hide();
	}

	Vector<String> nodes;
	
	class TransistorModel {
	    int pnp;
	    double beta;
	    double cjc, cje;
	    double rc, re, rb;
	}
	
	class VoltageSource {
	    String node1, node2;
	}

	String elmDump, ldump;
	
	void skipToken(BetterStringTokenizer st, String tok) throws Exception {
	    if (!st.nextToken().equals(tok))
		throw new Exception();
	}
	
	Vector<String> getLines(String text) {
	    Vector<String> vec = new Vector<String>();
	    
	    StringTokenizer lineSt = new StringTokenizer(text, "\n\r");
	    while (lineSt.hasMoreTokens()) {
		String s = lineSt.nextToken();
		if (s.startsWith("+") && vec.size() > 0)
		    vec.set(vec.size()-1, vec.lastElement() + s.substring(1));
		else
		    vec.add(s);
	    }
	    return vec;
	}
	void parseCircuit() {
	    String text = textArea.getText();
	    Vector<String> lines = getLines(text);
//	    Vector<String> externalNodes = new Vector<String>();
	    Vector<String> elements = new Vector<String>();
	    Vector<String> voltageSourcesToSuppress = new Vector<String>();
	    HashMap<String,TransistorModel> transistorModels = new HashMap<String,TransistorModel>();
	    HashMap<String,VoltageSource> voltageSources = new HashMap<String,VoltageSource>();
	    nodes = new Vector<String>();
	    Vector<ExtListEntry> extList = new Vector<ExtListEntry>();
	    String subcircuitName = null;
	    outputArea.setText("");
	    
	    // first pass, get a list of models and nodes
	    int ln;
	    for (ln = 0; ln != lines.size(); ln++) {
		String line = lines.get(ln);
		output("got line " + line);
		BetterStringTokenizer st = new BetterStringTokenizer(line, " \t\f()");
		if (!st.hasMoreTokens())
		    continue;
		String first = st.nextToken();
		if (first.startsWith("*"))
		    continue;
		try {
		    if (first.startsWith(".subckt")) {
			if (subcircuitName != null) {
			    output("multiple subcircuits!");
			    return;
			}
			subcircuitName = st.nextToken();
			while (st.hasMoreTokens()) {
			    String n = st.nextToken();
			    nodes.add(n);
//			    externalNodes.add(n);
			    extList.add(new ExtListEntry(n, nodes.size()));
			}
			continue;
		    }
		    if (first.equals(".ends"))
			continue;
		    if (first.startsWith(".model")) {
			String name = st.nextToken();
			String type = st.nextToken();
			if (type.equalsIgnoreCase("pnp") || type.equalsIgnoreCase("npn")) {
			    TransistorModel tm = new TransistorModel();
			    tm.pnp = (type.equalsIgnoreCase("pnp")) ? -1 : 1;
			    tm.beta = 100;
			    while (st.hasMoreTokens()) {
				String s = st.nextToken();
				if (s.startsWith("bf=")) {
				    double bf = parseNumber(s.substring(3));
				    tm.beta = bf;
				}
				if (s.startsWith("cje="))
				    tm.cje = parseNumber(s.substring(4));
				if (s.startsWith("cjc="))
				    tm.cjc = parseNumber(s.substring(4));
				if (s.startsWith("rc="))
				    tm.rc = parseNumber(s.substring(3));
				if (s.startsWith("re="))
				    tm.re = parseNumber(s.substring(3));
				if (s.startsWith("rb="))
				    tm.rb = parseNumber(s.substring(3));
			    }
			    output("found transistor model " + name);
			    transistorModels.put(name, tm);
			}
			if (type.equalsIgnoreCase("d")) {
			    DiodeModel dm = DiodeModel.getModelWithName(subcircuitName + "-" + name);
			    while (st.hasMoreTokens()) {
				String s = st.nextToken();
				if (s.startsWith("is="))
				    dm.saturationCurrent = parseNumber(s.substring(3));
				else if (s.startsWith("rs="))
				    dm.seriesResistance = parseNumber(s.substring(3));
				else if (s.startsWith("n="))
				    dm.emissionCoefficient = parseNumber(s.substring(2));
				else if (s.startsWith("bv="))
				    dm.breakdownVoltage = parseNumber(s.substring(3));
			    }
			}
			continue;
		    }
		    if (first.startsWith(".rename")) {
			String fromStr = st.nextToken();
			String toStr = st.nextTokenPreserveCase();
			int i;
			for (i = 0; i != extList.size(); i++)
			    if (extList.get(i).name.equalsIgnoreCase(fromStr)) {
				extList.get(i).setName(toStr);
				break;
			    }
			if (i == extList.size())
			    throw new Exception();
			continue;
		    }

		    elements.add(first);
		    int nodeCount = 0;
		    char c = first.charAt(0);
		    if ("bcdfhirv".indexOf(c) >= 0)
			nodeCount = 2;
		    else if ("q".indexOf(c) >= 0)
			nodeCount = 3;
		    else if (c == 'e' || c == 'g')
			nodeCount = 2;     // might be POLY, just look at first two nodes
		    else if (c == 'x') {
			output("nested subcircuits not supported");
			return;
		    } else {
			output("can't understand line: " + line);
			return;
		    }
		    int i;
		    if (nodeCount == 0)
			output("no nodes in " + first);
		    
		    // need to keep track of voltage sources and their nodes to implement f and h
		    if (c == 'v') {
			String n1 = st.nextToken();
			String n2 = st.nextToken();
			if (!nodes.contains(n1) && !n1.equals("0"))
			    nodes.add(n1);
			if (!nodes.contains(n2) && !n2.equals("0"))
			    nodes.add(n2);
			VoltageSource vs = new VoltageSource();
			vs.node1 = n1;
			vs.node2 = n2;
			voltageSources.put(first, vs);
			continue;
		    }
		    
		    for (i = 0; i != nodeCount; i++) {
			String n = st.nextToken();
			if (!nodes.contains(n) && !n.equals("0"))
			    nodes.add(n);
		    }
		    if (c == 'f' || c == 'h')
			voltageSourcesToSuppress.add(st.nextToken());
		} catch (Exception e) {
		    output("exception when parsing line: " + line);
		    CirSim.debugger();
		}
	    }
	    
	    output("nodes: " + nodes);
	    
	    // second pass
	    elmDump = "";
	    String dump = "";
	    int extraNode = nodes.size()+1;
	    for (ln = 0; ln != lines.size(); ln++) {
		String line = lines.get(ln);
		BetterStringTokenizer st = new BetterStringTokenizer(line, " \t\f");
		if (!st.hasMoreTokens())
		    continue;
		String first = st.nextToken();
		if (first.startsWith("*"))
		    continue;
		try {
		    if (first.equals(".subckt")) {
			while (st.hasMoreTokens())
			    nodes.add(st.nextToken());
			continue;
		    }
		    if (first.equals(".ends") || first.equals(".model"))
			continue;
		    char c = first.charAt(0);
		    if (c == 'c') {
			String n1 = st.nextToken();
			String n2 = st.nextToken();
			double cap = parseNumber(st.nextToken());
			elmDump += "CapacitorElm " + findNode(n1) + " "+ findNode(n2) + "\r";
			// add FLAG_BACK_EULER because that is probably the most appropriate setting for spice models
			ldump = "2 " + cap + " 0 0";
		    } else if (c == 'r') {
			String n1 = st.nextToken();
			String n2 = st.nextToken();
			String restxt = st.nextToken();
			// skip model if present
			char cd = restxt.charAt(0);
			if (!(cd >= '0' && cd <= '9'))
			    restxt = st.nextToken();
			double res = parseNumber(restxt);
			elmDump += "ResistorElm " + findNode(n1) + " "+ findNode(n2) + "\r";
			ldump = "0 " + res;
		    } else if (c == 'd') {
			String n1 = st.nextToken();
			String n2 = st.nextToken();
			String mod = st.nextToken();
			elmDump += "DiodeElm " + findNode(n1) + " "+ findNode(n2) + "\r";
			// 2 = FLAG_MODEL
			ldump = "2 " + CustomLogicModel.escape(subcircuitName + "-" + mod);
		    } else if (c == 'i') {
			String n1 = st.nextToken();
			String n2 = st.nextToken();
			String x = st.nextToken();
			if (x.equalsIgnoreCase("dc"))
			    x = st.nextToken();
			double cur = parseNumber(x);
			elmDump += "CurrentElm " + findNode(n1) + " "+ findNode(n2) + "\r";
			ldump = "0 " + cur;
		    } else if (c == 'q') {
			String n1 = st.nextToken();
			String n2 = st.nextToken();
			String n3 = st.nextToken();
			String mod = st.nextToken();
			TransistorModel tm = transistorModels.get(mod);
			int collector = findNode(n1);
			int base = findNode(n2);
			int emitter = findNode(n3);
			if (tm.rc > 0) {
			    // add resistor at collector
			    int n = extraNode++;
			    elmDump += "ResistorElm " + collector + " " + n + "\r";
			    if (dump.length() > 0)
				dump += " ";
			    dump += CustomLogicModel.escape("0 " + tm.rc);
			    collector = n;
			}
			if (tm.rb > 0) {
			    // add resistor at base
			    int n = extraNode++;
			    elmDump += "ResistorElm " + base + " " + n + "\r";
			    if (dump.length() > 0)
				dump += " ";
			    dump += CustomLogicModel.escape("0 " + tm.rb);
			    base = n;
			}
			if (tm.re > 0) {
			    // add resistor at emitter
			    int n = extraNode++;
			    elmDump += "ResistorElm " + emitter + " " + n + "\r";
			    if (dump.length() > 0)
				dump += " ";
			    dump += CustomLogicModel.escape("0 " + tm.re);
			    emitter = n;
			}
			if (tm.cje > 0) {
			    // add capacitor for base-emitter junction
			    elmDump += "CapacitorElm " + base + " " + emitter + "\r";
			    if (dump.length() > 0)
				dump += " ";
			    dump += CustomLogicModel.escape("0 " + tm.cje + " 0 0");
			}
			if (tm.cjc > 0) {
			    // add capacitor for base-collector junction
			    elmDump += "CapacitorElm " + base + " " + collector + "\r";
			    if (dump.length() > 0)
				dump += " ";
			    dump += CustomLogicModel.escape("0 " + tm.cjc + " 0 0");
			}
			elmDump += "TransistorElm " + base + " " + collector + " " + emitter + " " + "\r";
			ldump = "0 " + tm.pnp + " 0 0 " + tm.beta;
		    } else if (c == 'v') {
			// don't want to write this out if it's used for a current-controlled source.
			// not necessary and causes the source to not work.
			if (voltageSourcesToSuppress.contains(first))
			    continue;
			String n1 = st.nextToken();
			String n2 = st.nextToken();
			String x = st.nextToken();
			if (x.equalsIgnoreCase("dc"))
			    x = st.nextToken();
			double v = parseNumber(x);
			elmDump += "VoltageElm " + findNode(n2) + " "+ findNode(n1) + "\r";
			ldump = "0 0 0 " + v;
		    } else if (c == 'e') {
			parseControlledSource("VCVSElm", st);
		    } else if (c == 'g') {
			parseControlledSource("VCCSElm", st);
		    } else if (c == 'b') {
			parseBSource(st);
		    } else if (c == 'h') {
			String n1 = st.nextToken();
			String n2 = st.nextToken();
			String vcontrol = st.nextToken();
			double mult = parseNumber(st.nextToken());
			// find voltage source used to sense current and get its nodes
			VoltageSource vs = voltageSources.get(vcontrol);
			elmDump += "CCVSElm " + findNode(vs.node1) + " " + findNode(vs.node2) + " " + findNode(n1) + " " + findNode(n2) + "\r";
			ldump = "0 2 " + mult + "*i";
		    } else if (c == 'f') {
			String n1 = st.nextToken();
			String n2 = st.nextToken();
			String vcontrol = st.nextToken();
			double mult = parseNumber(st.nextToken());
			VoltageSource vs = voltageSources.get(vcontrol);
			// swap n1 and n2 because positive current flows from C- to C+
			elmDump += "CCCSElm " + findNode(vs.node1) + " " + findNode(vs.node2) + " " + findNode(n2) + " " + findNode(n1) + "\r";
			ldump = "0 2 " + mult + "*i";
		    } else {
			output("skipping " + first);
			continue;
		    }
		    if (dump.length() > 0)
			dump += " ";
		    dump += CustomLogicModel.escape(ldump);
		} catch (Exception e) {
		    output("exception when parsing line: " + line);
		    return;
		}
	    }
	    if (subcircuitName == null) {
		output("No model!");
		return;
	    }
	    output("elmDump\n" + elmDump);
	    output("\ndump\n" + dump);
	    
	    CustomCompositeModel ccm = new CustomCompositeModel();
	    ccm.elmDump = dump;
	    ccm.nodeList = elmDump;
	    ccm.extList = extList;
	    
	    EditCompositeModelDialog dlg = new EditCompositeModelDialog();
	    dlg.defaultName = subcircuitName;
	    if (!dlg.loadModel(ccm))
	        return;
	    closeDialog();
	    dlg.createDialog();
	    CirSim.dialogShowing = dlg;
	    dlg.show();
	}
	
	void parseBSource(BetterStringTokenizer st) throws Exception {
	    String outn1 = st.nextToken();
	    String outn2 = st.nextToken();
	    st.setDelimiters("*+=/(,) ");
	    skipToken(st, "i");
	    parseControlledSourceExpr(st, "VCCSElm", outn2, outn1);
	}
	
	void parseControlledSourceExpr(BetterStringTokenizer st, String cls, String outn1, String outn2) throws Exception {
	    Vector<String> inputs = new Vector<String>();
	    String expr = "";
	    skipToken(st, "=");
	    while (st.hasMoreTokens()) {
		String s = st.nextToken();
		if (s == "{")
		    continue;
		if (s == "}")
		    break;
		if (s == "v") {
		    skipToken(st, "(");
		    String n1 = st.nextToken();
		    int inct = inputs.size();
		    inputs.add(n1);
		    String n2 = null;
		    String s2 = st.nextToken();
		    if (s2.equals(",")) {
			n2 = st.nextToken();
			s2 = st.nextToken();
			inputs.add(n2);
			expr += "(" + getLetter(inct) + "-" + getLetter(inct+1) + ")";
		    } else
			expr += getLetter(inct);
		    if (!s2.equals(")"))
			throw new Exception();
		    output("nodes " + n1 + " and " + n2);
		} else
		    expr += s;
	    }
	    elmDump += cls;
	    int i;
	    for (i = 0; i != inputs.size(); i++)
		elmDump += " " + findNode(inputs.get(i));
	    elmDump += " " + findNode(outn1) + " " + findNode(outn2) + "\r";
	    ldump = "0 " + inputs.size() + " " + expr;
	}
	
	String getLetter(int ch) {
	    return Character.toString((char)('a'+ch));
	}
	
	void parseControlledSource(String cls, BetterStringTokenizer st) throws Exception {
	    String n1 = st.nextToken();
	    String n2 = st.nextToken();
	    // swap output nodes for current sources because current flows the opposite way
	    if (cls.endsWith("CSElm")) {
		String x = n1;
		n1 = n2;
		n2 = x;
	    }
	    st.setDelimiters(" =");
	    String n3 = st.nextToken();
	    if (n3.equals("value")) {
		st.setDelimiters(" ={}()+-*/,");
		parseControlledSourceExpr(st, cls, n1, n2);
		return;
	    }
	    if (!n3.startsWith("poly")) {
		// simple linear controlled source with two control inputs
		String n4 = st.nextToken();
		double mult = parseNumber(st.nextToken());
		elmDump += cls + " " + findNode(n3) + " " + findNode(n4) + " " + findNode(n1) + " " + findNode(n2) + "\r";
		ldump = "0 2 " + mult + "*(a-b)";
		return;
	    }
	    
	    // need to parse POLY, first get dimension
	    int dim = getPolyDim(n3);
	    
	    // get inputs
	    String inputs[] = new String[dim*2];
	    String inputExprs[] = new String[dim];
	    int i;
	    elmDump += cls + " ";
	    int inputCount = 0;
	    
	    // parse list of inputs of form (a,b)
	    for (i = 0; i != dim; i++) {
		StringTokenizer st2 = new StringTokenizer(st.nextToken(), "(),");
		String na = st2.nextToken();
		inputs[inputCount] = na;
		inputExprs[i] = getLetter(inputCount++);
		if (st2.hasMoreTokens()) {
		    String nb = st2.nextToken();
		    if (!nb.equals("0")) {
			inputs[inputCount] = nb;
			inputExprs[i] = "(" + Character.toString((char)('a'+inputCount-1)) + "-" + Character.toString((char)('a'+inputCount)) + ")";
			inputCount++;
		    }
		}
	    }
	    for (i = 0; i != inputCount; i++)
		elmDump += findNode(inputs[i]) + " ";
	    
	    elmDump += findNode(n1) + " " + findNode(n2) + "\r";
	    
	    String expr = "";
	    
	    // convert POLY to expression
	    for (i = 0; st.hasMoreTokens(); i++) {
		double x = Double.parseDouble(st.nextToken());
		if (x == 0)
		    continue;
		if (expr.length() > 0)
		    expr += "+";
		if (i == 0) {
		    expr = Double.toString(x);
		    continue;
		}
		// handle a few higher elements of POLY(2)
		if (dim == 2 && (i >= 3 && i <= 5)) {
		    expr += Double.toString(x) + "*" + inputExprs[i > 3 ? 1 : 0] + "*" + inputExprs[i > 4 ? 1 : 0];
		    continue;
		}
		// other than that, we only deal with the first n+1 terms of POLY(n).  need to improve this
		if (i > dim)
		    throw new Exception();
		expr += Double.toString(x) + "*" + inputExprs[i-1];
	    }
	    CirSim.debugger();
	    ldump = "0 " + inputCount + " " + expr;
	}
	
	int getPolyDim(String s) {
	    int ix = s.indexOf('(');
	    return Integer.parseInt(s.substring(ix+1, s.length()-1));
	}
	
	double parseNumber(String str) throws Exception {
	    // NumberFormat.parse() can't handle lowercase e, so make it uppercase.
	    // spice M means milli, so make it lowercase.
	    str = str.replaceAll("e", "E").replaceAll("M", "m");
	    return EditDialog.parseUnits(str);
	}
	
	int findNode(String node) {
	    return nodes.indexOf(node) + 1;
	}
	
	void output(String str) {
	    outputArea.setText(outputArea.getText() + str + "\n");
	}
}
